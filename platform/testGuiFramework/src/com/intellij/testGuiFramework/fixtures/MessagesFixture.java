// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.testGuiFramework.framework.Timeouts;
import com.intellij.ui.messages.SheetController;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Timeout;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.testGuiFramework.fixtures.IdeaDialogFixture.getDialogWrapperFrom;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

public class MessagesFixture<C extends Container> implements ContainerFixture<C> {

  /**
   * Could be a dialog (JDialog) or Mac message panel
   */
  private final C myTarget;
  private final Robot myRobot;

  @NotNull
  @Override
  public C target() {
    return myTarget;
  }

  @NotNull
  @Override
  public Robot robot() {
    return myRobot;
  }

  @NotNull
  public static MessagesFixture findByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title) {
      if (Messages.canShowMacSheetPanel()) {
        return findMacMessageByTitle(robot, root, title);
      }
      JDialog dialog = findByTitle(robot, title);
      return new MessagesFixture<>(robot, dialog);
  }

  @NotNull
  public static MessagesFixture findByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title, @NotNull Timeout timeout) {
    if (Messages.canShowMacSheetPanel()) {
      return findMacMessageByTitle(robot, root, title, timeout);
    }
    JDialog dialog = findByTitle(robot, title, timeout);
    return new MessagesFixture<>(robot, dialog);
  }

  @NotNull
  public static MessagesFixture findAny(@NotNull Robot robot, @NotNull Container root) {
    return findAny(robot, root, Timeouts.INSTANCE.getMinutes05());
  }

  @NotNull
  public static MessagesFixture findAny(@NotNull Robot robot, @NotNull Container root, @NotNull Timeout timeout) {
    if (Messages.canShowMacSheetPanel()) {
      return findMacMessageAny(robot, root, timeout);
    }
    JDialog dialog = findAny(robot, timeout);
    return new MessagesFixture<>(robot, dialog);
  }



  public static boolean exists(@NotNull Robot robot, @NotNull Container root, @NotNull String title) {
    try{
      findByTitle(robot, root, title);
      return true;
    } catch (AssertionError | WaitTimedOutError e) {
      return false;
    }
  }

  /**
   * @param messageContainer could be dialog (for Windows/Linux) or panel (for Mac)
   */
  private MessagesFixture(Robot robot, C messageContainer) {
    myTarget = messageContainer;
    myRobot = robot;
  }

  @NotNull
  public MessagesFixture clickOk() {
    return click("OK");
  }


  @NotNull
  public MessagesFixture clickYes() {
    return click("Yes");
  }

  @NotNull
  public MessagesFixture click(@NotNull String text) {
    GuiTestUtil.INSTANCE.findAndClickButton(this, text);
    return this;
  }


  @NotNull
  public MessagesFixture requireMessageContains(@NotNull String message) {
    String actual = getMessage();
    assertThat(actual).contains(message);
    return this;
  }

  public void clickCancel() {
    click("Cancel");
  }

  public static boolean isMessageDialog(@NotNull JDialog dialog) {
    DialogWrapper wrapper = getDialogWrapperFrom(dialog, DialogWrapper.class);
    if (wrapper != null) {
      if(wrapper instanceof MessageDialog){
        return true;
      }
    }
    return false;
  }

  @NotNull
  static JDialog findByTitle(@NotNull Robot robot, @NotNull final String title) {
    return findByTitle(robot, title, Timeouts.INSTANCE.getMinutes05());
  }

  @NotNull
  static JDialog findByTitle(@NotNull Robot robot, @NotNull final String title, @NotNull Timeout timeout) {
    return GuiTestUtil.INSTANCE.waitUntilFound(robot, null, new GenericTypeMatcher<>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (!title.equals(dialog.getTitle()) || !dialog.isShowing()) {
          return false;
        }
        return isMessageDialog(dialog);
      }
    }, timeout);
  }

  @NotNull
  static JDialog findAny(@NotNull Robot robot, @NotNull Timeout timeout) {
    return GuiTestUtil.INSTANCE.waitUntilFound(robot, null, new GenericTypeMatcher<>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return isMessageDialog(dialog);
      }
    }, timeout);
  }


  @NotNull
  static MacMessageFixture findMacMessageByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title) {
    return findMacMessageByTitle(robot, root, title, Timeouts.INSTANCE.getMinutes05());
  }

  @NotNull
  static MacMessageFixture findMacMessageByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title, @NotNull Timeout timeout) {
    JPanel sheetPanel = getSheetPanel(robot, root, timeout);

    String sheetTitle = getTitle(sheetPanel, robot);
    assertThat(sheetTitle).as("Sheet title").isEqualTo(title);

    return new MacMessageFixture(robot, sheetPanel);
  }


  private static MacMessageFixture findMacMessageAny(@NotNull Robot robot, @NotNull Container root) {
    return findMacMessageAny(robot, root, Timeouts.INSTANCE.getMinutes05());
  }

  private static MacMessageFixture findMacMessageAny(@NotNull Robot robot, @NotNull Container root, @NotNull Timeout timeout) {
    JPanel sheetPanel = getSheetPanel(robot, root, timeout);
    return new MacMessageFixture(robot, sheetPanel);
  }

  @NotNull
  private static JPanel getSheetPanel(@NotNull Robot robot, @NotNull Container root) {
    return getSheetPanel(robot, root, Timeouts.INSTANCE.getMinutes05());
  }

  @NotNull
  private static JPanel getSheetPanel(@NotNull Robot robot, @NotNull Container root, @NotNull Timeout timeout) {
    return GuiTestUtil.INSTANCE.waitUntilFound(robot, root, new GenericTypeMatcher<>(JPanel.class) {
      @Override
      protected boolean isMatching(@NotNull JPanel panel) {
        if (panel.getClass().getName().startsWith(SheetController.class.getName()) && panel.isShowing()) {
          SheetController controller = findSheetController(panel);
          JPanel sheetPanel1 = field("mySheetPanel").ofType(JPanel.class).in(controller).get();
          if (sheetPanel1 == panel) {
            return true;
          }
        }
        return false;
      }
    }, timeout);
  }

  @Nullable
  public String getTitle() {
    return ((JDialog)myTarget).getTitle();
  }

  @Nullable
  public static String getTitle(@NotNull JPanel sheetPanel, @NotNull Robot robot) {
    final JEditorPane messageTextPane = getMessageTextPane(sheetPanel);

    JEditorPane titleTextPane = robot.finder().find(sheetPanel, new GenericTypeMatcher<>(JEditorPane.class) {
      @Override
      protected boolean isMatching(@NotNull JEditorPane editorPane) {
        return editorPane != messageTextPane;
      }
    });

    return getHtmlBody(titleTextPane.getText());
  }

  @NotNull
  public String getMessage() {
    final JTextPane textPane = robot().finder().findByType(target(), JTextPane.class);
    //noinspection ConstantConditions
    return execute(new GuiQuery<>() {
      @Override
      protected String executeInEDT() throws Throwable {
        return StringUtil.notNullize(textPane.getText());
      }
    });
  }

  private static class MacMessageFixture extends MessagesFixture<JPanel> {

    MacMessageFixture(@NotNull Robot robot, @NotNull JPanel target) {
      super(robot, target);
    }

    @Override
    @Nullable
    public String getTitle(){
      final JEditorPane messageTextPane = getMessageTextPane(target());

      JEditorPane titleTextPane = robot().finder().find(target(), new GenericTypeMatcher<>(JEditorPane.class) {
        @Override
        protected boolean isMatching(@NotNull JEditorPane editorPane) {
          return editorPane != messageTextPane;
        }
      });

      return getHtmlBody(titleTextPane.getText());
    }

    @Override
    @NotNull
    public String getMessage() {
      JEditorPane messageTextPane = getMessageTextPane(target());
      String text = getHtmlBody(messageTextPane.getText());
      return StringUtil.notNullize(text);
    }
  }

  @NotNull
  private static JEditorPane getMessageTextPane(@NotNull JPanel sheetPanel) {
    SheetController sheetController = findSheetController(sheetPanel);
    JEditorPane messageTextPane = field("messageTextPane").ofType(JEditorPane.class).in(sheetController).get();
    assertNotNull(messageTextPane);
    return messageTextPane;
  }

  @NotNull
  public static SheetController findSheetController(@NotNull JPanel sheetPanel) {
    SheetController sheetController = field("this$0").ofType(SheetController.class).in(sheetPanel).get();
    assertNotNull(sheetController);
    return sheetController;
  }

  @Nullable
  private static String getHtmlBody(@NotNull String html) {
    try {
      Element rootElement = JDOMUtil.load(html);
      String sheetTitle = rootElement.getChild("body").getText();
      return sheetTitle.replace("\n", "").trim();
    }
    catch (Throwable e) {
      Logger.getInstance(MessagesFixture.class).info("Failed to parse HTML '" + html + "'", e);
    }
    return null;
  }
}
