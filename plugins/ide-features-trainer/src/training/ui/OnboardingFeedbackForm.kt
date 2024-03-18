// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.dialog.COMMON_FEEDBACK_SYSTEM_INFO_VERSION
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.impl.*
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import com.intellij.ui.ColorUtil
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.LicensingFacade
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.json.*
import org.jetbrains.annotations.Nls
import training.dsl.LessonUtil
import training.lang.LangSupport
import training.learn.LearnBundle
import training.statistic.FeedbackEntryPlace
import training.statistic.FeedbackLikenessAnswer
import training.statistic.StatisticBase
import training.util.OnboardingFeedbackData
import training.util.findLanguageSupport
import training.util.iftNotificationGroup
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLDocument

private const val FEEDBACK_CONTENT_WIDTH = 500
private const val SUB_OFFSET = 20

/** Increase the additional number when onboarding feedback format is changed */
private const val FEEDBACK_JSON_VERSION = COMMON_FEEDBACK_SYSTEM_INFO_VERSION + 1

private const val TIME_SCOPE_FOR_RESULT_COLLECTION_IN_DAYS = 120

// Key for PropertiesComponent to check whether to show onboarding feedback notification or not
fun getFeedbackProposedPropertyName(langSupport: LangSupport): String {
  val ideName = langSupport.defaultProductName?.let {
    if (it == "GoLand") "go" else it.lowercase()
  } ?: error("Lang support should implement 'defaultProductName': $langSupport")
  return "ift.$ideName.onboarding.feedback.proposed"
}

fun shouldCollectFeedbackResults(): Boolean {
  val buildDate = ApplicationInfo.getInstance().buildDate
  val buildToCurrentPeriod = buildDate.toInstant().toKotlinInstant().periodUntil(Clock.System.now(), TimeZone.currentSystemDefault())
  return buildToCurrentPeriod.days <= TIME_SCOPE_FOR_RESULT_COLLECTION_IN_DAYS
}

fun showOnboardingFeedbackNotification(project: Project?, onboardingFeedbackData: OnboardingFeedbackData) {
  onboardingFeedbackData.feedbackHasBeenProposed()
  StatisticBase.logOnboardingFeedbackNotification(getFeedbackEntryPlace(project))
  val notification = iftNotificationGroup.createNotification(LearnBundle.message("onboarding.feedback.notification.title"),
                                                             LearnBundle.message("onboarding.feedback.notification.message",
                                                                                 LessonUtil.productName),
                                                             NotificationType.INFORMATION)
  notification.addAction(object : NotificationAction(LearnBundle.message("onboarding.feedback.notification.action")) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      notification.expire()
      showOnboardingLessonFeedbackForm(project, onboardingFeedbackData, true)
    }
  })
  notification.notify(project)
}

fun showOnboardingLessonFeedbackForm(project: Project?,
                                     onboardingFeedbackData: OnboardingFeedbackData,
                                     openedViaNotification: Boolean): Boolean {
  onboardingFeedbackData.feedbackHasBeenProposed()
  val saver = mutableListOf<JsonObjectBuilder.() -> Unit>()

  fun feedbackTextArea(fieldName: String, optionalText: @Nls String, width: Int, height: Int): JBScrollPane {
    val jTextPane = JBTextArea()
    jTextPane.lineWrap = true
    jTextPane.wrapStyleWord = true
    jTextPane.emptyText.text = optionalText
    jTextPane.emptyText.setFont(JBFont.regular())
    jTextPane.border = JBEmptyBorder(3, 5, 3, 5)
    jTextPane.font = JBFont.regular()

    saver.add {
      put(fieldName, jTextPane.text)
    }

    val scrollPane = JBScrollPane(jTextPane)
    scrollPane.preferredSize = Dimension(width, height)
    return scrollPane
  }

  fun feedbackOption(fieldName: String, text: @NlsContexts.Label String): FeedbackOption {
    val result = FeedbackOption(text)
    saver.add {
      put(fieldName, result.isChosen)
    }
    return result
  }

  val freeForm = feedbackTextArea("overall_experience",
                                  LearnBundle.message("onboarding.feedback.empty.text.overall.experience"),
                                  FEEDBACK_CONTENT_WIDTH, 100)

  val technicalIssuesArea = feedbackTextArea("other_issues",
                                             LearnBundle.message("onboarding.feedback.empty.text.other.issues"),
                                             FEEDBACK_CONTENT_WIDTH - SUB_OFFSET, 65)

  val technicalIssuesPanel = FormBuilder.createFormBuilder().let { builder ->
    builder.addComponent(technicalIssuesArea)
    builder.panel
  }
  technicalIssuesPanel.isVisible = false
  technicalIssuesPanel.border = JBUI.Borders.emptyLeft(SUB_OFFSET)

  val experiencedUserOption = feedbackOption("experienced_user", LearnBundle.message("onboarding.feedback.option.experienced.user"))

  val (votePanel, likenessResult) = createLikenessPanel()
  saver.add {
    put("like_vote", likenessToString(likenessResult()))
  }

  val systemInfoData = CommonFeedbackSystemData.getCurrentData()

  val recentProjectsNumber = RecentProjectsManagerBase.getInstanceEx().getRecentPaths().size
  val actionsNumber = service<ActionsLocalSummary>().getActionsStats().keys.size

  val emailCheckBox = JBCheckBox(LearnBundle.message("onboarding.feedback.email.consent"))

  val jLabel = JLabel(LearnBundle.message("onboarding.feedback.form.email"))
  jLabel.isEnabled = false
  val emailTextField = JBTextField(LicensingFacade.INSTANCE?.getLicenseeEmail() ?: "")
  emailTextField.disabledTextColor = UIUtil.getComboBoxDisabledForeground()
  emailTextField.isEnabled = false

  val emailLine = JPanel().also { panel ->
    panel.isOpaque = false
    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
    panel.add(jLabel)
    panel.add(emailTextField)
  }

  emailCheckBox.addItemListener {
    emailCheckBox.isSelected.let {
      jLabel.isEnabled = it
      emailTextField.isEnabled = it
    }
  }

  val agreement1 = createAgreementTextPane(LearnBundle.message("onboarding.feedback.user.agreement.info"), false) {
    showSystemData(project, systemInfoData, onboardingFeedbackData, recentProjectsNumber, actionsNumber)
  }
  val agreement2 = createCollapsableAgreement()

  val technicalIssuesOption = feedbackOption("technical_issues", LearnBundle.message("onboarding.feedback.option.technical.issues"))
  val header = JLabel(LearnBundle.message("onboarding.feedback.option.form.header")).also {
    it.font = UISettings.getInstance().getFont(5).deriveFont(Font.BOLD)
    it.border = JBUI.Borders.empty(24 - UIUtil.DEFAULT_VGAP, 0, 20 - UIUtil.DEFAULT_VGAP, 0)
  }
  val wholePanel = FormBuilder.createFormBuilder()
    .addComponent(header)
    .addComponent(JLabel(LearnBundle.message("onboarding.feedback.question.how.did.you.like")))
    .addComponent(votePanel)
    .addComponent(JLabel(LearnBundle.message("onboarding.feedback.question.any.problems")).also {
      it.border = JBUI.Borders.emptyTop(20 - UIUtil.DEFAULT_VGAP)
    })
    .addComponent(technicalIssuesOption)
    .addComponent(technicalIssuesPanel)
    .addComponent(feedbackOption("useless", LearnBundle.message("onboarding.feedback.option.tour.is.useless")))
    .addComponent(feedbackOption("very_long", LearnBundle.message("onboarding.feedback.option.too.many.steps")))
    .addComponent(JLabel(LearnBundle.message("onboarding.feedback.label.overall.experience")).also {
      it.border = JBUI.Borders.empty(20 - UIUtil.DEFAULT_VGAP, 0, 12 - UIUtil.DEFAULT_VGAP, 0)
    })
    .addComponent(freeForm)
    .addComponent(agreement1.also { it.border = JBUI.Borders.emptyBottom(20 - UIUtil.DEFAULT_VGAP) })
    .addComponent(emailCheckBox.also { it.border = JBUI.Borders.emptyBottom(6 - UIUtil.DEFAULT_VGAP) })
    .addComponent(emailLine.also { it.border = JBUI.Borders.emptyBottom(14 - UIUtil.DEFAULT_VGAP) })
    .addComponent(agreement2)
    .panel

  val dialog = object : DialogWrapper(project) {
    override fun createCenterPanel(): JComponent = wholePanel

    init {
      title = LearnBundle.message("onboarding.feedback.dialog.title")
      setOKButtonText(LearnBundle.message("onboarding.feedback.confirm.button"))
      setCancelButtonText(LearnBundle.message("onboarding.feedback.reject.button"))
      init()
    }
  }

  dialog.isResizable = false

  installSubPanelLogic(technicalIssuesOption, technicalIssuesPanel, wholePanel, dialog)

  val maySendFeedback = dialog.showAndGet()
  if (maySendFeedback) {
    val jsonConverter = Json

    val collectedData = buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, onboardingFeedbackData.feedbackReportId)
      put("format_version", FEEDBACK_JSON_VERSION + onboardingFeedbackData.additionalFeedbackFormatVersion)
      for (function in saver) {
        function()
      }
      put("system_info", jsonConverter.encodeToJsonElement(systemInfoData))
      onboardingFeedbackData.addAdditionalSystemData.invoke(this)
      put("lesson_end_info", jsonConverter.encodeToJsonElement(onboardingFeedbackData.lessonEndInfo))
      put("used_actions", actionsNumber)
      put("recent_projects", recentProjectsNumber)
    }

    val description = getShortDescription(likenessResult(), technicalIssuesOption, freeForm)
    val feedbackData = FeedbackRequestDataWithDetailedAnswer(
      (if (emailCheckBox.isSelected) emailTextField.text else ""),
      onboardingFeedbackData.reportTitle,
      description,
      DEFAULT_FEEDBACK_CONSENT_ID,
      true,
      emptyList(),
      onboardingFeedbackData.feedbackReportId,
      collectedData
    )

    submitFeedback(feedbackData, {}, {}, getFeedbackRequestType())
    ThanksForFeedbackNotification().notify(project)
  }
  StatisticBase.logOnboardingFeedbackDialogResult(
    place = getFeedbackEntryPlace(project),
    hasBeenSent = maySendFeedback,
    openedViaNotification = openedViaNotification,
    likenessAnswer = likenessResult(),
    experiencedUser = experiencedUserOption.isChosen
  )
  return maySendFeedback
}

private fun getFeedbackRequestType() = when(Registry.stringValue("ift.send.onboarding.feedback")) {
  "production" -> FeedbackRequestType.PRODUCTION_REQUEST
  "staging" -> FeedbackRequestType.TEST_REQUEST
  else -> FeedbackRequestType.NO_REQUEST
}

private fun getShortDescription(likenessResult: FeedbackLikenessAnswer,
                                technicalIssuesOption: FeedbackOption,
                                freeForm: JBScrollPane): String {
  val likenessSummaryAnswer = likenessToString(likenessResult)

  return """
Likeness answer: $likenessSummaryAnswer
Has technical problems: ${technicalIssuesOption.isChosen}
Overall experience:
${(freeForm.viewport.view as? JBTextArea)?.text}
    """.trimIndent()
}

private fun likenessToString(likenessResult: FeedbackLikenessAnswer) = when (likenessResult) {
  FeedbackLikenessAnswer.LIKE -> "like"
  FeedbackLikenessAnswer.DISLIKE -> "dislike"
  FeedbackLikenessAnswer.NO_ANSWER -> "no answer"
}

private fun showSystemData(project: Project?,
                           systemInfoData: CommonFeedbackSystemData,
                           onboardingFeedbackData: OnboardingFeedbackData?,
                           recentProjectsNumber: Int,
                           actionsNumber: Int) {
  showFeedbackSystemInfoDialog(project, systemInfoData) {
    if (onboardingFeedbackData != null) {
      onboardingFeedbackData.addRowsForUserAgreement.invoke(this)
      val lessonEndInfo = onboardingFeedbackData.lessonEndInfo
      row(LearnBundle.message("onboarding.feedback.system.recent.projects.number")) {
        label(recentProjectsNumber.toString())
      }
      row(LearnBundle.message("onboarding.feedback.system.actions.used")) {
        label(actionsNumber.toString())
      }
      row(LearnBundle.message("onboarding.feedback.system.lesson.completed")) {
        label(lessonEndInfo.lessonPassed.toString())
      }
      row(LearnBundle.message("onboarding.feedback.system.visual.step.on.end")) {
        label(lessonEndInfo.currentVisualIndex.toString())
      }
      row(LearnBundle.message("onboarding.feedback.system.technical.index.on.end")) {
        label(lessonEndInfo.currentTaskIndex.toString())
      }
    }
  }
}

private fun createLikenessPanel(): Pair<NonOpaquePanel, () -> FeedbackLikenessAnswer> {
  val votePanel = NonOpaquePanel()
  votePanel.layout = BoxLayout(votePanel, BoxLayout.X_AXIS)
  val likeAnswer = FeedbackOption(AllIcons.Ide.Like, AllIcons.Ide.LikeSelected)
  votePanel.add(likeAnswer)
  val dislikeAnswer = FeedbackOption(AllIcons.Ide.Dislike, AllIcons.Ide.DislikeSelected)
  votePanel.add(dislikeAnswer)
  likeAnswer.addActionListener {
    // the listener is triggered before the actual field change
    if (dislikeAnswer.isChosen) {
      dislikeAnswer.action?.actionPerformed(null)
    }
  }

  dislikeAnswer.addActionListener {
    // the listener is triggered before the actual field change
    if (likeAnswer.isChosen) {
      likeAnswer.action?.actionPerformed(null)
    }
  }

  val result = {
    when {
      likeAnswer.isChosen -> FeedbackLikenessAnswer.LIKE
      dislikeAnswer.isChosen -> FeedbackLikenessAnswer.DISLIKE
      else -> FeedbackLikenessAnswer.NO_ANSWER
    }
  }
  return votePanel to result
}


private fun installSubPanelLogic(feedbackOption: FeedbackOption, feedbackSubPanel: JPanel, wholePanel: JPanel, dialog: DialogWrapper) {
  feedbackOption.addActionListener {
    val needShow = !feedbackOption.isChosen
    if (feedbackSubPanel.isVisible == needShow) return@addActionListener
    val oldPreferredSize = wholePanel.preferredSize
    val oldSize = dialog.window.size
    feedbackSubPanel.isVisible = needShow
    val newPreferredSize = wholePanel.preferredSize
    dialog.window.size = Dimension(oldSize.width, oldSize.height + newPreferredSize.height - oldPreferredSize.height)
  }
}

private fun getLikenessIcon(icon: Icon): Icon = IndentedIcon(icon, JBUI.insets(6))

private class FeedbackOption(@NlsContexts.Label text: String?, unselectedIcon: Icon?, selectedIcon: Icon?) : JButton() {
  var isChosen = false

  constructor(@NlsContexts.Label text: String) : this(text, null, null)
  constructor(unselectedIcon: Icon, selectedIcon: Icon) : this(null, getLikenessIcon(unselectedIcon), getLikenessIcon(selectedIcon))

  init {
    putClientProperty("styleTag", true)
    isFocusable = false
    action = object : AbstractAction(text, unselectedIcon) {
      override fun actionPerformed(e: ActionEvent?) {
        isChosen = !isChosen
        if (isChosen) {
          icon = selectedIcon
        } else {
          icon = unselectedIcon
        }
      }
    }
  }

  override fun paint(g: Graphics) {
    // These colors are hardcoded because there are no corresponding keys
    // But the feedback dialog should appear for the newcomers, and it is expected they will not significantly customize IDE at that moment
    val hoverBackgroundColor = JBColor(Color(0xDFDFDF), Color(0x4C5052))
    val selectedBackgroundColor = JBColor(Color(0xFFFFFF), Color(0x313335))
    val unselectedForeground = JBColor(Color(0x000000), Color(0xBBBBBB))
    val selectedForeground = JBColor(Color(0x000000), Color(0xFEFEFE))

    val backgroundColor = when {
      isChosen -> selectedBackgroundColor
      mousePosition != null -> hoverBackgroundColor
      else -> JBColor.namedColor("Panel.background", Color.WHITE)
    }
    val foregroundColor = if (isChosen) selectedForeground else unselectedForeground

    putClientProperty("JButton.backgroundColor", backgroundColor)
    foreground = foregroundColor
    super.paint(g)
  }
}

private fun createCollapsableAgreement(): JComponent {
  val prefix = LearnBundle.message("onboarding.feedback.user.agreement.prefix")
  val suffix = LearnBundle.message("onboarding.feedback.user.agreement.suffix")
  val shortText = prefix + " " + LearnBundle.message("onboarding.feedback.user.agreement.more")
  val longText = prefix + " " + suffix + " " + LearnBundle.message("onboarding.feedback.user.agreement.less")
  var shortForm = true
  val jTextPane = createAgreementTextPane(shortText, true) {
    shortForm = !shortForm
    text = if (shortForm) shortText else longText
  }

  val scrollPane = JBScrollPane(jTextPane)
  scrollPane.preferredSize = Dimension(FEEDBACK_CONTENT_WIDTH, 100)
  scrollPane.border = null
  return scrollPane
}

private fun createAgreementTextPane(@Nls htmlText: String, customLink: Boolean, showSystemInfo: JTextPane.() -> Unit): JTextPane {
  return JTextPane().apply {
    contentType = "text/html"
    addHyperlinkListener(object : HyperlinkAdapter() {
      override fun hyperlinkActivated(e: HyperlinkEvent) {
        showSystemInfo()
      }
    })
    editorKit = HTMLEditorKitBuilder.simple()
    text = htmlText

    val styleSheet = (document as HTMLDocument).styleSheet
    val textColor = "#" + ColorUtil.toHex(UIUtil.getContextHelpForeground())
    styleSheet.addRule("body { color: $textColor; font-size:${JBUI.Fonts.label().lessOn(3f)}pt;}")
    if (customLink) {
      styleSheet.addRule("a, a:link { color: $textColor; text-decoration: underline;}")
    }
    isEditable = false
  }
}

private fun getFeedbackEntryPlace(project: Project?) = when {
  project == null -> FeedbackEntryPlace.WELCOME_SCREEN
  findLanguageSupport(project) != null -> FeedbackEntryPlace.LEARNING_PROJECT
  else -> FeedbackEntryPlace.ANOTHER_PROJECT
}
