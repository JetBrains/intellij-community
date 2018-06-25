// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.javafx;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class JavaFxHtmlPanel implements Disposable {
  @NotNull
  private final JPanel myPanelWrapper;
  @NotNull
  private final List<Runnable> myInitActions = new ArrayList<>();
  @Nullable
  private JFXPanel myPanel;
  @Nullable protected WebView myWebView;

  public JavaFxHtmlPanel() {
    myPanelWrapper = new JPanel(new BorderLayout());
    myPanelWrapper.setBackground(JBColor.background());

    ApplicationManager.getApplication().invokeLater(() -> runFX(() -> PlatformImpl.startup(() -> {
      myWebView = new WebView();
      myWebView.setContextMenuEnabled(false);
      myWebView.setZoom(JBUI.scale(1.f));

      final WebEngine engine = myWebView.getEngine();
      registerListeners(engine);

      final Scene scene = new Scene(myWebView);

      ApplicationManager.getApplication().invokeLater(() -> runFX(() -> {
        myPanel = new JFXPanelWrapper();

        Platform.runLater(() -> myPanel.setScene(scene));

        setHtml("");
        for (Runnable action : myInitActions) {
          Platform.runLater(action);
        }
        myInitActions.clear();

        myPanelWrapper.add(myPanel, BorderLayout.CENTER);
        myPanelWrapper.repaint();
      }));
    })));
  }

  protected void registerListeners(@NotNull WebEngine engine) {
  }

  private static void runFX(@NotNull Runnable r) {
    IdeEventQueue.unsafeNonblockingExecute(r);
  }

  protected void runInPlatformWhenAvailable(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myPanel == null) {
      myInitActions.add(runnable);
    }
    else {
      Platform.runLater(runnable);
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myPanelWrapper;
  }

  public void setHtml(@NotNull String html) {
    final String htmlToRender = prepareHtml(html);
    runInPlatformWhenAvailable(() -> getWebViewGuaranteed().getEngine().loadContent(htmlToRender));
  }

  @NotNull
  protected String prepareHtml(@NotNull String html) {
    return html;
  }

  public void render() {
    runInPlatformWhenAvailable(() -> {
      getWebViewGuaranteed().getEngine().reload();
      ApplicationManager.getApplication().invokeLater(myPanelWrapper::repaint);
    });
  }

  @Override
  public void dispose() {
  }

  @NotNull
  protected WebView getWebViewGuaranteed() {
    if (myWebView == null) {
      throw new IllegalStateException("WebView should be initialized by now. Check the caller thread");
    }
    return myWebView;
  }
}
