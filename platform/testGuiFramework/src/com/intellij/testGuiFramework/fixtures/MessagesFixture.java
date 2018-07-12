/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.ui.messages.SheetController;
import com.intellij.util.JdomKt;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.fest.swing.timing.Timeout;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.junit.Assert.assertNotNull;

public class MessagesFixture {
  @NotNull private final ContainerFixture<? extends Container> myDelegate;

  @NotNull
  public static MessagesFixture findByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title) {
      if (Messages.canShowMacSheetPanel()) {
        return new MessagesFixture(findMacSheetByTitle(robot, root, title));
      }
      MessageDialogFixture dialog = MessageDialogFixture.findByTitle(robot, title);
      return new MessagesFixture(dialog);
  }

  @NotNull
  public static MessagesFixture findByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title, @NotNull Timeout timeout) {
    if (Messages.canShowMacSheetPanel()) {
      return new MessagesFixture(findMacSheetByTitle(robot, root, title, timeout));
    }
    MessageDialogFixture dialog = MessageDialogFixture.findByTitle(robot, title, timeout);
    return new MessagesFixture(dialog);
  }

  @NotNull
  public static MessagesFixture findAny(@NotNull Robot robot, @NotNull Container root) {
    return findAny(robot, root, GuiTestUtil.INSTANCE.getLONG_TIMEOUT());
  }

  @NotNull
  public static MessagesFixture findAny(@NotNull Robot robot, @NotNull Container root, @NotNull Timeout timeout) {
    if (Messages.canShowMacSheetPanel()) {
      return new MessagesFixture(findMacSheetAny(robot, root, timeout));
    }
    MessageDialogFixture dialog = MessageDialogFixture.findAny(robot, timeout);
    return new MessagesFixture(dialog);
  }



  public static boolean exists(@NotNull Robot robot, @NotNull Container root, @NotNull String title) {
    try{
      findByTitle(robot, root, title);
      return true;
    } catch (AssertionError | WaitTimedOutError e) {
      return false;
    }
  }

  private MessagesFixture(@NotNull ContainerFixture<? extends Container> delegate) {
    myDelegate = delegate;
  }

  @NotNull
  public MessagesFixture clickOk() {
    GuiTestUtil.INSTANCE.findAndClickOkButton(myDelegate);
    return this;
  }


  @NotNull
  public MessagesFixture clickYes() {
    return click("Yes");
  }

  @NotNull
  public MessagesFixture click(@NotNull String text) {
    GuiTestUtil.INSTANCE.findAndClickButton(myDelegate, text);
    return this;
  }

  @NotNull
  public String getMessage() {
    return ((Delegate)myDelegate).getMessage();
  }

  @NotNull
  public MessagesFixture requireMessageContains(@NotNull String message) {
    String actual = ((Delegate)myDelegate).getMessage();
    assertThat(actual).contains(message);
    return this;
  }

  public void clickCancel() {
    GuiTestUtil.INSTANCE.findAndClickCancelButton(myDelegate);
  }

  @NotNull
  static JPanelFixture findMacSheetByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title) {
    return findMacSheetByTitle(robot, root, title, GuiTestUtil.INSTANCE.getLONG_TIMEOUT());
  }

  @NotNull
  static JPanelFixture findMacSheetByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title, @NotNull Timeout timeout) {
    JPanel sheetPanel = getSheetPanel(robot, root, timeout);

    String sheetTitle = getTitle(sheetPanel, robot);
    assertThat(sheetTitle).as("Sheet title").isEqualTo(title);

    return new MacSheetPanelFixture(robot, sheetPanel);
  }


  private static JPanelFixture findMacSheetAny(@NotNull Robot robot, @NotNull Container root) {
    return findMacSheetAny(robot, root, GuiTestUtil.INSTANCE.getLONG_TIMEOUT());
  }

  private static JPanelFixture findMacSheetAny(@NotNull Robot robot, @NotNull Container root, @NotNull Timeout timeout) {
    JPanel sheetPanel = getSheetPanel(robot, root, timeout);
    return new MacSheetPanelFixture(robot, sheetPanel);
  }

  @NotNull
  private static JPanel getSheetPanel(@NotNull Robot robot, @NotNull Container root) {
    return getSheetPanel(robot, root, GuiTestUtil.INSTANCE.getLONG_TIMEOUT());
  }

  @NotNull
  private static JPanel getSheetPanel(@NotNull Robot robot, @NotNull Container root, @NotNull Timeout timeout) {
    return GuiTestUtil.INSTANCE.waitUntilFound(robot, root, new GenericTypeMatcher<JPanel>(JPanel.class) {
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
    if (myDelegate instanceof MacSheetPanelFixture) return ((MacSheetPanelFixture)myDelegate).getTitle();
    return ((MessageDialogFixture)myDelegate).target().getTitle();
  }

  @Nullable
  public static String getTitle(@NotNull JPanel sheetPanel, @NotNull Robot robot) {
    final JEditorPane messageTextPane = getMessageTextPane(sheetPanel);

    JEditorPane titleTextPane = robot.finder().find(sheetPanel, new GenericTypeMatcher<JEditorPane>(JEditorPane.class) {
      @Override
      protected boolean isMatching(@NotNull JEditorPane editorPane) {
        return editorPane != messageTextPane;
      }
    });

    return getHtmlBody(titleTextPane.getText());
  }

  @Nullable
  public <T extends JComponent> T find(GenericTypeMatcher<T> matcher) {
    return myDelegate.robot().finder().find(myDelegate.target(), matcher);
  }

  interface Delegate {
    @NotNull String getMessage();
  }

  private static class MacSheetPanelFixture extends JPanelFixture implements Delegate {
    public MacSheetPanelFixture(@NotNull Robot robot, @NotNull JPanel target) {
      super(robot, target);
    }


    @Nullable
    public String getTitle(){
      final JEditorPane messageTextPane = getMessageTextPane(target());

      JEditorPane titleTextPane = robot().finder().find(target(), new GenericTypeMatcher<JEditorPane>(JEditorPane.class) {
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
      Element rootElement = JdomKt.loadElement(html);
      String sheetTitle = rootElement.getChild("body").getText();
      return sheetTitle.replace("\n", "").trim();
    }
    catch (Throwable e) {
      Logger.getInstance(MessagesFixture.class).info("Failed to parse HTML '" + html + "'", e);
    }
    return null;
  }
}
