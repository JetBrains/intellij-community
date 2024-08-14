// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.FindManager.MalformedReplacementStringException
import com.intellij.find.FindModel
import com.intellij.find.findUsages.similarity.MostCommonUsagePatternsComponent
import com.intellij.find.findUsages.similarity.MostCommonUsagePatternsComponent.Companion.findClusteringSessionInUsageView
import com.intellij.ide.IdeTooltipManager
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usages.UsageContextPanel
import com.intellij.usages.UsageView
import com.intellij.usages.UsageViewPresentation
import com.intellij.usages.similarity.clustering.ClusteringSearchSession
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Contract
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Point
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.util.concurrent.Callable
import java.util.regex.Pattern
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.Pair

open class UsagePreviewPanel @JvmOverloads constructor(project: Project,
                                                       presentation: UsageViewPresentation,
                                                       private val myIsEditor: Boolean = false)
  : UsageContextPanelBase(presentation), UiCompatibleDataProvider {

  private var myEditor: Editor? = null
  private var myLineHeight = 0
  private var myCachedSelectedUsageInfos: List<UsageInfo>? = null
  private var myCachedSearchPattern: Pattern? = null
  private var myCachedReplaceString: String? = null
  private var myCachedCaseSensitive = false
  private val myPropertyChangeSupport = PropertyChangeSupport(this)
  private var myPreviousSelectedGroupNodes: Set<GroupNode> = HashSet()
  private var myToolbarWithSimilarUsagesLink: UsagePreviewToolbarWithSimilarUsagesLink? = null
  private var myMostCommonUsagePatternsComponent: MostCommonUsagePatternsComponent? = null
  private val cs = UsageViewCoroutineScopeProvider.getInstance(project).coroutineScope.childScope()
  private var myShowTooltipBalloon = Registry.`is`("ide.find.show.tooltip.in.preview")

  override fun uiDataSnapshot(sink: DataSink) {
    val editor = myEditor ?: return
    sink[CommonDataKeys.EDITOR] = editor
    val position = editor.caretModel.logicalPosition
    val project = editor.project ?: return
    sink.lazy(CommonDataKeys.NAVIGATABLE_ARRAY) {
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return@lazy null
      arrayOf<Navigatable>(OpenFileDescriptor(project, file, position.line, position.column))
    }
  }

  fun setShowTooltipBalloon(showTooltipBalloon: Boolean) {
    myShowTooltipBalloon = showTooltipBalloon
  }

  @Internal
  class Provider : UsageContextPanel.Provider {
    override fun create(usageView: UsageView): UsageContextPanel {
      return UsagePreviewPanel((usageView as UsageViewImpl).project, usageView.getPresentation(), true)
    }

    override fun isAvailableFor(usageView: UsageView): Boolean {
      return true
    }

    override fun getTabTitle(): String {
      return UsageViewBundle.message("tab.title.preview")
    }
  }

  private suspend fun resetEditor(infos: List<UsageInfo>) {
    val pair: Pair<PsiFile, Document> = readAction {
      val psiElement = infos[0].element ?: return@readAction null
      var psiFile = psiElement.containingFile ?: return@readAction null
      val project = psiFile.project
      val host = InjectedLanguageManager.getInstance(project).getInjectionHost(psiFile)
      if (host != null) {
        psiFile = host.containingFile ?: return@readAction null
      }
      val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return@readAction null
      psiFile to document
    } ?: return

    withContext(Dispatchers.EDT) {
      val (psiFile, document) = pair
      val project = psiFile.project

      if (myEditor == null || document !== myEditor!!.document) {
        releaseEditor()
        removeAll()
        if (isDisposed) return@withContext
        //maybe readaction
        writeIntentReadAction {
          myEditor = createEditor(psiFile, document)
          lineHeight = myEditor!!.lineHeight
          myEditor!!.setBorder(if (myIsEditor) null else JBEmptyBorder(0, UIUtil.LARGE_VGAP, 0, 0))
          add(myEditor!!.component, BorderLayout.CENTER)
        }
        invalidate()
        validate()
      }

      PsiDocumentManager.getInstance(project).performForCommittedDocument(document, Runnable {
        if (infos != myCachedSelectedUsageInfos // avoid moving viewport
            || !UsageViewPresentation.arePatternsEqual(myCachedSearchPattern, myPresentation.searchPattern)
            || myCachedReplaceString != myPresentation.replaceString || myCachedCaseSensitive != myPresentation.isCaseSensitive) {
          //maybe readaction
          WriteIntentReadAction.run {
            highlight(infos, myEditor!!, project, myShowTooltipBalloon, HighlighterLayer.ADDITIONAL_SYNTAX)
          }
          myCachedSelectedUsageInfos = infos
          myCachedSearchPattern = myPresentation.searchPattern
          myCachedCaseSensitive = myPresentation.isCaseSensitive
          myCachedReplaceString = myPresentation.replaceString
        }
      })
    }
  }

  var lineHeight: Int
    get() {
      ThreadingAssertions.assertEventDispatchThread()
      return myLineHeight
    }
    private set(lineHeight) {
      if (lineHeight != myLineHeight) {
        val oldHeight = myLineHeight
        myLineHeight = lineHeight
        myPropertyChangeSupport.firePropertyChange(LINE_HEIGHT_PROPERTY, oldHeight, myLineHeight)
      }
    }

  override fun addPropertyChangeListener(propertyName: String, listener: PropertyChangeListener) {
    myPropertyChangeSupport.addPropertyChangeListener(propertyName, listener)
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener)
  }

  private fun createEditor(psiFile: PsiFile, document: Document): Editor {
    if (LOG.isDebugEnabled) {
      LOG.debug("Creating preview for " + psiFile.virtualFile)
    }
    val project = psiFile.project
    val editor = if (myIsEditor) {
      EditorFactory.getInstance().createEditor(document, project, EditorKind.PREVIEW)
    }
    else {
      EditorFactory.getInstance().createViewer(document, project, EditorKind.PREVIEW)
    }
    if (editor is EditorEx) {
      val disposable = (editor as? EditorImpl)?.disposable ?: project
      ReadAction
        .nonBlocking(
          Callable {
            EditorHighlighterFactory.getInstance().createEditorHighlighter(project, psiFile.virtualFile).also {
              it.setText(editor.document.immutableCharSequence)
            }
          })
        .finishOnUiThread(ModalityState.any()) { result: EditorHighlighter? ->
          if (result != null) editor.setHighlighter(result)
        }
        .expireWith(disposable)
        .submit(NonUrgentExecutor.getInstance())
    }
    customizeEditorSettings(editor.settings)
    editor.putUserData(PREVIEW_EDITOR_FLAG, this)

    onEditorCreated(editor)

    return editor
  }

  private fun customizeEditorSettings(settings: EditorSettings) {
    settings.isLineMarkerAreaShown = myIsEditor
    settings.isShowIntentionBulb = myIsEditor
    settings.isFoldingOutlineShown = false
    settings.additionalColumnsCount = 0
    settings.additionalLinesCount = 0
    settings.isAnimatedScrolling = false
    settings.isAutoCodeFoldingEnabled = false
  }

  override fun dispose() {
    isDisposed = true
    cs.cancel("dispose")
    val project = myEditor?.project
    releaseEditor()
    disposeAndRemoveSimilarUsagesToolbar()
    for (editor in EditorFactory.getInstance().allEditors) {
      if (editor.project === project && editor.getUserData(PREVIEW_EDITOR_FLAG) === this) {
        LOG.error("Editor was not released:$editor")
      }
    }
  }

  private fun disposeAndRemoveSimilarUsagesToolbar() {
    if (myToolbarWithSimilarUsagesLink != null) {
      remove(myToolbarWithSimilarUsagesLink)
      revalidate()
      Disposer.dispose(myToolbarWithSimilarUsagesLink!!)
      myToolbarWithSimilarUsagesLink = null
    }
  }

  fun releaseEditor() {
    ThreadingAssertions.assertEventDispatchThread()
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor!!)
      myEditor = null
      myCachedSelectedUsageInfos = null
      myCachedSearchPattern = null
      myCachedReplaceString = null
    }
  }

  @Deprecated("Implementation details of UsagePreviewPanel")
  fun getCannotPreviewMessage(infos: List<UsageInfo>): String? {
    return cannotPreviewMessage(infos)
  }

  @RequiresEdt
  public override fun updateLayoutLater(infos: List<UsageInfo>, usageView: UsageView) {
    disposeAndRemoveSimilarUsagesToolbar()
    val usageViewImpl = usageView as? UsageViewImpl
    if (ClusteringSearchSession.isSimilarUsagesClusteringEnabled() && usageViewImpl != null) {
      val sessionInUsageView = findClusteringSessionInUsageView(usageViewImpl)
      val selectedGroupNodes = usageViewImpl.selectedGroupNodes()
      if (isOnlyGroupNodesSelected(infos, selectedGroupNodes) && sessionInUsageView != null) {
        showMostCommonUsagePatterns(usageViewImpl, selectedGroupNodes, sessionInUsageView)
      }
      else {
        updateLayoutLater(infos)
        updateSimilarUsagesToolBar(infos, usageView)
      }
      myPreviousSelectedGroupNodes = selectedGroupNodes
    }
    else {
      updateLayoutLater(infos)
    }
  }

  private fun updateSimilarUsagesToolBar(infos: List<UsageInfo>, usageView: UsageView) {
    if (Registry.`is`("similarity.find.usages.show.similar.usages.in.usage.preview")) {
      val session = findClusteringSessionInUsageView(usageView)
      if (session != null) {
        val cluster = session.findCluster(infos.firstOrNull())
        if (cluster != null && cluster.usages.size > 1) {
          val toolbarWithSimilarUsagesLink = UsagePreviewToolbarWithSimilarUsagesLink(this, usageView, infos, cluster, session)
          myToolbarWithSimilarUsagesLink = toolbarWithSimilarUsagesLink

          add(toolbarWithSimilarUsagesLink, BorderLayout.NORTH)
        }
      }
    }
  }

  private fun showMostCommonUsagePatterns(usageViewImpl: UsageViewImpl,
                                          selectedGroupNodes: Set<GroupNode>, session: ClusteringSearchSession) {
    if (myPreviousSelectedGroupNodes != selectedGroupNodes) {
      releaseEditor()
      removeAll()
      if (myMostCommonUsagePatternsComponent != null) {
        Disposer.dispose(myMostCommonUsagePatternsComponent!!)
      }
      myMostCommonUsagePatternsComponent = MostCommonUsagePatternsComponent(usageViewImpl, session)
      Disposer.register(this, myMostCommonUsagePatternsComponent!!)
      add(myMostCommonUsagePatternsComponent)
      myMostCommonUsagePatternsComponent!!.loadSnippets()
    }
  }

  override fun updateLayoutLater(infos: List<UsageInfo>?) {
    cs.launch(ModalityState.current().asContextElement()) {
      previewUsages(infos)
    }
  }

  private suspend fun previewUsages(infos: List<UsageInfo>?) {
    val cannotPreviewMessage = readAction { cannotPreviewMessage(infos) }
    if (cannotPreviewMessage == null) {
      resetEditor(infos!!)
    }
    else {
      withContext(Dispatchers.EDT) {
        releaseEditor()
        removeAll()
        val newLineIndex = cannotPreviewMessage.indexOf('\n')
        if (newLineIndex == -1) {
          emptyText.setText(cannotPreviewMessage)
        }
        else {
          emptyText
            .setText(cannotPreviewMessage.substring(0, newLineIndex))
            .appendSecondaryText(cannotPreviewMessage.substring(newLineIndex + 1), StatusText.DEFAULT_ATTRIBUTES, null)
        }
        revalidate()
      }
    }
  }

  private class TextView(html: @NlsSafe String) : JPanel() {
    override fun paintComponent(graphics: Graphics) {}

    init {
      add(createLabel(html))
    }

    private fun createLabel(html: @NlsSafe String): JLabel {
      val label: JLabel = JBLabel(html).setAllowAutoWrapping(true)
      label.foreground = JBColor(Gray._240, Gray._200)
      return label
    }
  }

  private class BalloonPositionTracker(private val myProject: Project,
                                       private val myEditor: Editor,
                                       private val myRange: TextRange,
                                       private val myBalloonText: String) : PositionTracker<Balloon?>(myEditor.contentComponent) {
    override fun recalculateLocation(balloon: Balloon): RelativePoint {
      val startOffset = myRange.startOffset
      val endOffset = myRange.endOffset
      if (!component.isDisplayable) {
        balloon.hide()
        component.addHierarchyListener(object : HierarchyListener {
          override fun hierarchyChanged(e: HierarchyEvent?) {
            if (component.isDisplayable) {
              showBalloon(myProject, myEditor, myRange, myBalloonText)
              component.removeHierarchyListener(this)
            }
          }
        })
      }
      if (!insideVisibleArea(myEditor, myRange)) {
        balloon.hide()
        myEditor.scrollingModel.addVisibleAreaListener(object : VisibleAreaListener {
          override fun visibleAreaChanged(e: VisibleAreaEvent) {
            if (insideVisibleArea(myEditor, myRange)) {
              showBalloon(myProject, myEditor, myRange, myBalloonText)
              myEditor.scrollingModel.removeVisibleAreaListener(this)
            }
          }
        })
      }
      val startPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset))
      val endPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset))
      val point = Point((startPoint.x + endPoint.x) / 2, endPoint.y + myEditor.lineHeight)
      return RelativePoint(myEditor.contentComponent, point)
    }
  }

  @Internal
  companion object {
    const val LINE_HEIGHT_PROPERTY = "UsageViewPanel.lineHeightProperty"
    private val LOG = Logger.getInstance(UsagePreviewPanel::class.java)

    private val IN_PREVIEW_USAGE_FLAG = Key.create<Boolean>("IN_PREVIEW_USAGE_FLAG")

    @JvmStatic
    fun highlight(infos: List<UsageInfo>,
                  editor: Editor,
                  project: Project,
                  showTooltipBalloon: Boolean,
                  highlightLayer: Int) {
      ThreadingAssertions.assertEventDispatchThread()
      LOG.assertTrue(PsiDocumentManager.getInstance(project).isCommitted(editor.document))
      val markupModel = editor.markupModel
      for (highlighter in markupModel.allHighlighters) {
        if (highlighter.getUserData(IN_PREVIEW_USAGE_FLAG) != null) {
          highlighter.dispose()
        }
      }
      val balloon = editor.getUserData(PREVIEW_BALLOON_KEY)
      if (balloon != null && !balloon.isDisposed) {
        Disposer.dispose(balloon)
        editor.putUserData(PREVIEW_BALLOON_KEY, null)
      }
      val findModel = getFindModel(editor)
      for (i in infos.indices.reversed()) { // finish with the first usage so that caret ends up there
        val info = infos[i]
        val psiElement = info.element
        if (psiElement == null || !psiElement.isValid) continue
        val infoRange = info.rangeInElement
        var rangeToHighlight = calculateHighlightingRangeForUsage(psiElement, infoRange)
        if (psiElement is PsiNamedElement && psiElement !is PsiFile) {
          rangeToHighlight = getNameElementTextRange(psiElement)
        }
        // highlight injected element in host document text range
        rangeToHighlight = InjectedLanguageManager.getInstance(psiElement.project).injectedToHost(psiElement, rangeToHighlight)
        val highlighter = markupModel.addRangeHighlighter(EditorColors.SEARCH_RESULT_ATTRIBUTES,
                                                          rangeToHighlight.startOffset,
                                                          rangeToHighlight.endOffset,
                                                          highlightLayer,
                                                          HighlighterTargetArea.EXACT_RANGE)
        highlighter.putUserData(IN_PREVIEW_USAGE_FLAG, true)
        if (infoRange != null && findModel != null && findModel.isReplaceState) {
          val boxHighlighter = markupModel.addRangeHighlighter(
            infoRange.startOffset,
            infoRange.endOffset,
            highlightLayer,
            TextAttributes(null, null, editor.colorsScheme.getColor(EditorColors.CARET_COLOR), EffectType.BOXED, Font.PLAIN),
            HighlighterTargetArea.EXACT_RANGE)
          boxHighlighter.putUserData(IN_PREVIEW_USAGE_FLAG, true)
          editor.caretModel.moveToOffset(infoRange.endOffset)
        }
        else {
          editor.caretModel.moveToOffset(rangeToHighlight.endOffset)
        }
        if (infos.size == 1 && infoRange != null) {
          var balloonText: String? = null
          if (findModel != null) {
            val replacementText =
              FindManager.getInstance(project).getStringToReplace(editor.document.getText(rangeToHighlight), findModel,
                                                                  rangeToHighlight.startOffset, editor.document.text) ?: return
            val previewText = createPreviewHtml(replacementText)
            if (previewText != findModel.stringToReplace || Registry.`is`("ide.find.show.replacement.hint.for.simple.regexp")) {
              balloonText = previewText
            }
          }
          else if (showTooltipBalloon) {
            balloonText = info.tooltipText
          }
          if (!balloonText.isNullOrEmpty()) showBalloon(project, editor, rangeToHighlight, balloonText)
        }
      }
      editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    /**
     * Attempts to find the name element of PsiNamedElement and return its range
     * @param psiElement an element to highlight
     * @return range to highlight for named element
     */
    @JvmStatic
    fun getNameElementTextRange(psiElement: PsiElement): TextRange {
      val psiFile = psiElement.containingFile
      val nameElement = psiFile.findElementAt(psiElement.textOffset)
      return if (nameElement != null) {
        nameElement.textRange
      }
      else psiElement.textRange
    }

    /**
     * Calculates the proper highlighting range for usage in preview. In some cases psiElement text range info is not fine (i.e. Non-code usages)
     * @param psiElement an element to highlight
     * @param infoRange the [UsageInfo.getRangeInElement] result in corresponding UsageInfo
     * @return range to highlight for in usage preview
     */
    @ApiStatus.Internal
    @JvmStatic
    fun calculateHighlightingRangeForUsage(psiElement: PsiElement, infoRange: ProperTextRange?): TextRange {
      val elementRange = psiElement.textRange
      return if (infoRange == null || infoRange.startOffset > elementRange.length || infoRange.endOffset > elementRange.length) elementRange
      else elementRange.cutOut(infoRange)
    }

    private val PREVIEW_BALLOON_KEY = Key.create<Balloon>("PREVIEW_BALLOON_KEY")
    private fun showBalloon(project: Project, editor: Editor, range: TextRange, balloonText: String) {
      ThreadingAssertions.assertEventDispatchThread()
      try {
        val balloon = createPreviewBalloon(balloonText)
        EditorUtil.disposeWithEditor(editor, balloon)
        balloon.show(BalloonPositionTracker(project, editor, range, balloonText), Balloon.Position.below)
        editor.putUserData(PREVIEW_BALLOON_KEY, balloon)
      }
      catch (e: MalformedReplacementStringException) {
        //Not a problem, just don't show balloon in this case
      }
    }

    private fun getFindModel(editor: Editor): FindModel? {
      val panel = editor.getUserData(PREVIEW_EDITOR_FLAG)
      var searchPattern: Pattern? = null
      var replaceString: String? = null
      if (panel != null) {
        searchPattern = panel.myPresentation.searchPattern
        replaceString = panel.myPresentation.replaceString
      }
      if (searchPattern == null || replaceString == null) {
        return null
      }
      val stub = FindModel()
      stub.isCaseSensitive = panel!!.myPresentation.isCaseSensitive
      stub.isPreserveCase = panel.myPresentation.isPreserveCase
      stub.isMultiline = true
      stub.isRegularExpressions = true
      stub.isReplaceAll = true
      stub.stringToFind = searchPattern.pattern()
      stub.stringToReplace = replaceString
      return stub
    }

    val PREVIEW_EDITOR_FLAG = Key.create<UsagePreviewPanel>("PREVIEW_EDITOR_FLAG")

    @Contract("null -> !null")
    private fun cannotPreviewMessage(infos: List<UsageInfo>?): @NlsContexts.StatusText String? {
      if (infos == null) {
        return UsageViewBundle.message("usage.preview.isnt.available")
      }
      if (ContainerUtil.isEmpty(infos)) {
        return UsageViewBundle.message("select.the.usage.to.preview")
      }
      var psiFile: PsiFile? = null
      for (info in infos) {
        val file = info.file
        if (psiFile == null) {
          psiFile = file
        }
        else {
          if (psiFile !== file) {
            return UsageViewBundle.message("several.occurrences.selected")
          }
        }
      }
      return null
    }

    @RequiresReadLock
    @RequiresBackgroundThread
    @JvmStatic
    fun isOneAndOnlyOnePsiFileInUsages(infos: List<UsageInfo>?) = cannotPreviewMessage(infos) == null

    private fun isOnlyGroupNodesSelected(infos: List<UsageInfo>, groupNodes: Set<GroupNode>): Boolean {
      return infos.isEmpty() && !groupNodes.isEmpty()
    }

    @JvmStatic
    fun createPreviewBalloon(html: @NlsSafe String): Balloon {
      return JBPopupFactory.getInstance().createBalloonBuilder(TextView(html))
        .setFadeoutTime(0)
        .setFillColor(IdeTooltipManager.GRAPHITE_COLOR)
        .setBorderColor(IdeTooltipManager.GRAPHITE_COLOR)
        .setAnimationCycle(0)
        .setHideOnClickOutside(false)
        .setHideOnKeyOutside(false)
        .setHideOnAction(false)
        .setCloseButtonEnabled(false)
        .setHideOnFrameResize(false)
        .createBalloon()
    }

    @JvmStatic
    fun createPreviewHtml(text: @NlsSafe String): @NlsSafe String {
      val htmlToShow: String
      if (text.isEmpty()) {
        htmlToShow = HtmlBuilder()
          .append("<" + FindBundle.message("live.preview.empty.string") + ">")
          .wrapWithHtmlBody()
          .toString()
      }
      else {
        val shortened = StringUtil.shortenTextWithEllipsis(text, 500, 0, true)
        htmlToShow = HtmlBuilder()
          .append(shortened.replace("\t", "    "))
          .wrapWith("code").wrapWith("pre").wrapWith("body").wrapWith("html")
          .toString()
      }
      return htmlToShow
    }

    private fun insideVisibleArea(e: Editor, r: TextRange): Boolean {
      ThreadingAssertions.assertEventDispatchThread()
      val textLength = e.document.textLength
      if (r.startOffset > textLength) return false
      if (r.endOffset > textLength) return false
      val visibleArea = e.scrollingModel.visibleArea
      visibleArea.height -= 2 * e.lineHeight
      visibleArea.y -= e.lineHeight
      val point = e.logicalPositionToXY(e.offsetToLogicalPosition(r.startOffset))
      return visibleArea.contains(point)
    }
  }
}
