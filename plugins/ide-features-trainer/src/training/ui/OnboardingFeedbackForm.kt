// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("HardCodedStringLiteral")

package training.ui

import com.intellij.feedback.createFeedbackAgreementComponent
import com.intellij.feedback.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.submitGeneralFeedback
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeBalloonLayoutImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.IconUtil
import com.intellij.util.ui.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import training.FeaturesTrainerIcons
import training.dsl.LessonUtil
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

private const val FEEDBACK_CONTENT_WIDTH = 500
private const val SUB_OFFSET = 20


fun showOnboardingFeedbackNotification(project: Project?, onboardingFeedbackData: OnboardingFeedbackData?) {
  StatisticBase.logOnboardingFeedbackNotification(getFeedbackEntryPlace(project))
  val notification = iftNotificationGroup.createNotification("Share feedback about creating the onboarding tour",
                                                             "This will help us improve learning experience in ${LessonUtil.productName}",
                                                             NotificationType.INFORMATION)
  notification.addAction(object : NotificationAction("Leave feedback") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      val feedbackHasBeenSent = showOnboardingLessonFeedbackForm(project, onboardingFeedbackData, true)
      notification.expire()
      if (feedbackHasBeenSent) {
        invokeLater {
          // It is needed to show "Thank you" notification
          (WelcomeFrame.getInstance()?.balloonLayout as? WelcomeBalloonLayoutImpl)?.showPopup()
        }
      }
    }
  })
  notification.notify(project)
}

fun showOnboardingLessonFeedbackForm(project: Project?,
                                     onboardingFeedbackData: OnboardingFeedbackData?,
                                     openedViaNotification: Boolean): Boolean {
  val saver = mutableListOf<JsonObjectBuilder.() -> Unit>()

  fun feedbackTextArea(fieldName: String, optionalText: String, width: Int, height: Int): JComponent {
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

  fun feedbackOption(fieldName: String, @NlsContexts.Label text: String): FeedbackOption {
    val result = FeedbackOption(text)
    saver.add {
      put(fieldName, result.isChosen)
    }
    return result
  }

  val freeForm = feedbackTextArea("overall_experience", "Optional", FEEDBACK_CONTENT_WIDTH, 100)

  val technicalIssuesArea = feedbackTextArea("other_issues", "Some other issues?", FEEDBACK_CONTENT_WIDTH - SUB_OFFSET, 65)

  val technicalIssuesPanel = FormBuilder.createFormBuilder()
    .addComponent(feedbackOption("cannot_pass", "Cannot pass task"))
    .addComponent(feedbackOption("interpreter_issues","Interpreter issues"))
    .addComponent(technicalIssuesArea)
    .panel
  technicalIssuesPanel.isVisible = false
  technicalIssuesPanel.border = JBUI.Borders.emptyLeft(SUB_OFFSET)

  val experiencedUserOption = feedbackOption("experienced_user", "I've used JetBrains IDEs (PyCharm, IDEA, WebStorm, etc)")
  val usefulPanel = FormBuilder.createFormBuilder()
    .addComponent(experiencedUserOption)
    .addComponent(feedbackOption("too_obvious","Shown information is too obvious"))
    .panel
  usefulPanel.isVisible = false
  usefulPanel.border = JBUI.Borders.emptyLeft(SUB_OFFSET)

  val (votePanel, likenessResult) = createLikenessPanel()
  saver.add {
    "like_vote" to when(likenessResult()) {
      FeedbackLikenessAnswer.LIKE -> "like"
      FeedbackLikenessAnswer.DISLIKE -> "dislike"
      FeedbackLikenessAnswer.NO_ANSWER -> ""
    }
  }

  val systemInfoData = CommonFeedbackSystemInfoData.getCurrentData()

  val recentProjectsNumber = RecentProjectsManagerBase.instanceEx.getRecentPaths().size
  val actionsNumber = service<ActionsLocalSummary>().getActionsStats().keys.size

  val agreement = createOnboardingAgreementComponent(project, systemInfoData, onboardingFeedbackData,
                                                     recentProjectsNumber,
                                                     actionsNumber)

  val technicalIssuesOption = feedbackOption("technical_issues","Technical issues")
  val unusefulOption = feedbackOption("useless","Tour wasn't useful for me")
  val header = JLabel("Share your feedback").also {
    it.font = UISettings.instance.getFont(5).deriveFont(Font.BOLD)
    it.border = JBUI.Borders.empty(24 - UIUtil.DEFAULT_VGAP, 0, 20 - UIUtil.DEFAULT_VGAP, 0)
  }
  val wholePanel = FormBuilder.createFormBuilder()
    .addComponent(header)
    .addComponent(JLabel("How did you like the onboarding tour?"))
    .addComponent(votePanel)
    .addComponent(JLabel("Did you encounter any problems?").also { it.border = JBUI.Borders.emptyTop(20 - UIUtil.DEFAULT_VGAP) })
    .addComponent(technicalIssuesOption)
    .addComponent(technicalIssuesPanel)
    .addComponent(feedbackOption("dislike_interactive","Don't like interactive learning"))
    .addComponent(feedbackOption("too_restrictive","The tasks are too restrictive"))
    .addComponent(unusefulOption)
    .addComponent(usefulPanel)
    .addComponent(feedbackOption("very_long","Too many steps"))
    .addComponent(JLabel("Share your overall experience or suggestions").also {
      it.border = JBUI.Borders.empty(20 - UIUtil.DEFAULT_VGAP, 0, 12 - UIUtil.DEFAULT_VGAP, 0)
    })
    .addComponent(freeForm)
    .addComponent(agreement.also { it.border = JBUI.Borders.emptyTop(18 - UIUtil.DEFAULT_VGAP) })
    .panel

  val dialog = object : DialogWrapper(project) {
    override fun createCenterPanel(): JComponent = wholePanel

    init {
      title = "Onbdoarding Tour Feedback"
      setOKButtonText("Send Feedback")
      setCancelButtonText("No, Thanks")
      init()
    }
  }

  dialog.isResizable = false

  installSubPanelLogic(technicalIssuesOption, technicalIssuesPanel, wholePanel, dialog)
  installSubPanelLogic(unusefulOption, usefulPanel, wholePanel, dialog)

  val maySendFeedback = dialog.showAndGet()
  if (maySendFeedback) {
    val jsonConverter = Json { }

    val collectedData = buildJsonObject {
      for (function in saver) {
        function()
      }
      put("system_info", jsonConverter.encodeToJsonElement(systemInfoData))
      if (onboardingFeedbackData != null) {
        onboardingFeedbackData.addAdditionalSystemData.invoke(this)
        put("lesson_end_info", jsonConverter.encodeToJsonElement(onboardingFeedbackData.lessonEndInfo))
        put("used_actions", actionsNumber)
        put("recent_projects", recentProjectsNumber)
      }
    }

    if (onboardingFeedbackData != null) {
      submitGeneralFeedback(project, onboardingFeedbackData.reportTitle, "",
                            onboardingFeedbackData.reportTitle, jsonConverter.encodeToString(collectedData))
    }
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

private fun createOnboardingAgreementComponent(project: Project?,
                                               systemInfoData: CommonFeedbackSystemInfoData,
                                               onboardingFeedbackData: OnboardingFeedbackData?,
                                               recentProjectsNumber: Int,
                                               actionsNumber: Int) =
  createFeedbackAgreementComponent(project) {
    // TODO: add specific information, like Python interpreters
    showFeedbackSystemInfoDialog(project, systemInfoData) {
      if (onboardingFeedbackData != null) {
        onboardingFeedbackData.addRowsForUserAgreement.invoke(this)
        val lessonEndInfo = onboardingFeedbackData.lessonEndInfo
        row("Recent projects number:") {
          label(recentProjectsNumber.toString())
        }
        row("Different IDE actions used:") {
          label(actionsNumber.toString())
        }
        row("Lesson completed:") {
          label(lessonEndInfo.lessonPassed.toString())
        }
        row("The visual step on end:") {
          label(lessonEndInfo.currentVisualIndex.toString())
        }
        row("The technical index on end:") {
          label(lessonEndInfo.currentTaskIndex.toString())
        }
      }
    }
  }

private fun createLikenessPanel(): Pair<NonOpaquePanel, () -> FeedbackLikenessAnswer> {
  val votePanel = NonOpaquePanel()
  val likeIcon = getLikenessIcon(FeaturesTrainerIcons.Img.Like)
  val dislikeIcon = getLikenessIcon(FeaturesTrainerIcons.Img.Dislike)
  votePanel.layout = BoxLayout(votePanel, BoxLayout.X_AXIS)
  val likeAnswer = FeedbackOption(likeIcon)
  votePanel.add(likeAnswer)
  val dislikeAnswer = FeedbackOption(dislikeIcon)
  votePanel.add(dislikeAnswer)
  likeAnswer.addActionListener {
    // the listener is triggered before the actual field change
    if (!likeAnswer.isChosen) {
      dislikeAnswer.isChosen = false
      dislikeAnswer.repaint()
    }
  }

  dislikeAnswer.addActionListener {
    // the listener is triggered before the actual field change
    if (!dislikeAnswer.isChosen) {
      likeAnswer.isChosen = false
      likeAnswer.repaint()
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

private fun getLikenessIcon(icon: Icon): Icon {
  return IndentedIcon(IconUtil.scale(icon, null, 0.25f), JBUI.insets(6))
}

private class FeedbackOption(@NlsContexts.Label text: String?, icon: Icon?) : JButton() {
  var isChosen = false

  constructor(@NlsContexts.Label text: String) : this(text, null)
  constructor(icon: Icon?) : this(null, icon)

  init {
    putClientProperty("styleTag", true)
    isFocusable = false
    action = object : AbstractAction(text, icon) {
      override fun actionPerformed(e: ActionEvent?) {
        isChosen = !isChosen
      }
    }
  }

  override fun paint(g: Graphics) {
    // These colors are hardcoded because there are no corresponding keys
    // But the feedback dialog should appear for the newcomers, and it is expected they will not significantly customize IDE at that moment
    val hoverBackgroundColor = JBColor(Color(0xDFDFDF), Color(0x4C5052))
    val selectedBackgroundColor = JBColor(Color(0xD5D5D5), Color(0x5C6164))
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

private fun getFeedbackEntryPlace(project: Project?) = when {
  project == null -> FeedbackEntryPlace.WELCOME_SCREEN
  findLanguageSupport(project) != null -> FeedbackEntryPlace.LEARNING_PROJECT
  else -> FeedbackEntryPlace.ANOTHER_PROJECT
}
