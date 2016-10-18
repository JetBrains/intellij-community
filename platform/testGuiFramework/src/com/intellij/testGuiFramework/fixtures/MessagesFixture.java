/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.util.JDOMUtil.loadDocument;
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

  private MessagesFixture(@NotNull ContainerFixture<? extends Container> delegate) {
    myDelegate = delegate;
  }

  @NotNull
  public MessagesFixture clickOk() {
    GuiTestUtil.findAndClickOkButton(myDelegate);
    return this;
  }



  @NotNull
  public MessagesFixture clickYes() {
    return click("Yes");
  }

  @NotNull
  public MessagesFixture click(@NotNull String text) {
    GuiTestUtil.findAndClickButton(myDelegate, text);
    return this;
  }

  @NotNull
  public MessagesFixture requireMessageContains(@NotNull String message) {
    String actual = ((Delegate)myDelegate).getMessage();
    assertThat(actual).contains(message);
    return this;
  }

  public void clickCancel() {
    GuiTestUtil.findAndClickCancelButton(myDelegate);
  }

  @NotNull
  static JPanelFixture findMacSheetByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title) {
    JPanel sheetPanel = GuiTestUtil.waitUntilFound(robot, root, new GenericTypeMatcher<JPanel>(JPanel.class) {
      @Override
      protected boolean isMatching(@NotNull JPanel panel) {
        if (panel.getClass().getName().startsWith(SheetController.class.getName()) && panel.isShowing()) {
          SheetController controller = findSheetController(panel);
          JPanel sheetPanel = field("mySheetPanel").ofType(JPanel.class).in(controller).get();
          if (sheetPanel == panel) {
            return true;
          }
        }
        return false;
      }
    }, GuiTestUtil.LONG_TIMEOUT);

    String sheetTitle = getTitle(sheetPanel, robot);
    assertThat(sheetTitle).as("Sheet title").isEqualTo(title);

    return new MacSheetPanelFixture(robot, sheetPanel);
  }

  @Nullable
  private static String getTitle(@NotNull JPanel sheetPanel, @NotNull Robot robot) {
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
  private static SheetController findSheetController(@NotNull JPanel sheetPanel) {
    SheetController sheetController = field("this$0").ofType(SheetController.class).in(sheetPanel).get();
    assertNotNull(sheetController);
    return sheetController;
  }

  @Nullable
  private static String getHtmlBody(@NotNull String html) {
    try {
      Document document = loadDocument(html);
      Element rootElement = document.getRootElement();
      String sheetTitle = rootElement.getChild("body").getText();
      return sheetTitle.replace("\n", "").trim();
    }
    catch (Throwable e) {
      Logger.getInstance(MessagesFixture.class).info("Failed to parse HTML '" + html + "'", e);
    }
    return null;
  }
}
