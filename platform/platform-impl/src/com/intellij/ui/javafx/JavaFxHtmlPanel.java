// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.javafx;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.sun.javafx.application.PlatformImpl;
import com.sun.javafx.webkit.Accessor;
import com.sun.webkit.WebPage;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaFxHtmlPanel implements Disposable {
  @NotNull
  private final JPanel myPanelWrapper;
  @NotNull
  private final List<Runnable> myInitActions = new ArrayList<>();
  @Nullable
  protected JFXPanel myPanel;
  @Nullable protected WebView myWebView;
  private Color background;

  private static final CefApp ourCefApp;
  private static final CefClient ourCefClient;
  private static final Map<CefBrowser, JavaFxHtmlPanel> ourBrowser2Panel = new HashMap<>();
  // browser requires some correct URL for loading
  private final static String ourUrl = JavaFxHtmlPanel.class.getResource(JavaFxHtmlPanel.class.getSimpleName() + ".class").toExternalForm();

  private @NotNull final CefBrowser myCefBrowser;
  private boolean myIsCefBrowserCreated;
  private @Nullable String myHtml;

  static {
    CefSettings settings = new CefSettings();
    settings.windowless_rendering_enabled = false;
    settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_ERROR;
    ourCefApp = CefApp.getInstance(settings);
    ourCefClient = ourCefApp.createClient();
    ourCefClient.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
      @Override
      public void onAfterCreated(CefBrowser browser) {
        JavaFxHtmlPanel panel = ourBrowser2Panel.get(browser);
        if (panel != null) {
          panel.myIsCefBrowserCreated = true;
          if (panel.myHtml != null) {
            browser.loadString(panel.myHtml, ourUrl);
            panel.myHtml = null;
          }
        }
      }
    });
    ourCefClient.addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadEnd(CefBrowser browser, CefFrame frame, int i) {
        JavaFxHtmlPanel panel = ourBrowser2Panel.get(browser);
        if (panel != null && browser.getURL() != null && panel.isHtmlLoaded()) {
          browser.getUIComponent().setSize(panel.myPanelWrapper.getSize());
          panel.myPanelWrapper.revalidate();
        }
      }
    });
  }

  public JavaFxHtmlPanel() {
    myPanelWrapper = new JPanel(new BorderLayout()) {
      @Override
      public void removeNotify() {
        super.removeNotify();
        ourBrowser2Panel.remove(myCefBrowser);
      }
      @Override
      public void addNotify() {
        ourBrowser2Panel.put(myCefBrowser, JavaFxHtmlPanel.this);
        super.addNotify();
      }
    };
    myPanelWrapper.setBackground(JBColor.background());

    myCefBrowser = ourCefClient.createBrowser("about:blank", false, false);

    // workaround for the UI garbage issue: keep the browser component min until the browser loads html
    myPanelWrapper.setLayout(null);
    myCefBrowser.getUIComponent().setSize(1, 1);
    myPanelWrapper.add(myCefBrowser.getUIComponent()/*, BorderLayout.CENTER*/);
    myPanelWrapper.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (isHtmlLoaded()) {
          myCefBrowser.getUIComponent().setSize(myPanelWrapper.getSize());
        }
      }
    });

    //engine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
    //  if (newValue == Worker.State.RUNNING) {
    //      WebPage page = Accessor.getPageFor(engine);
    //      page.setBackgroundColor(background.getRGB());
    //    }
    //  }
    //);

    //  javafx.scene.paint.Color fxColor = toFxColor(background);
    //  final Scene scene = new Scene(myWebView, fxColor);
    //
    //  ApplicationManager.getApplication().invokeLater(() -> runFX(() -> {
    //    myPanel = new JFXPanelWrapper();
    //
    //    Platform.runLater(() -> myPanel.setScene(scene));
    //
    //    setHtml("");
    //    for (Runnable action : myInitActions) {
    //      Platform.runLater(action);
    //    }
    //    myInitActions.clear();
    //
    //    myPanelWrapper.add(myPanel, BorderLayout.CENTER);
    //    myPanelWrapper.repaint();
    //  }));
    //})));
    //
    //LafManager.getInstance().addLafManagerListener(new JavaFXLafManagerListener());
    //runInPlatformWhenAvailable(() -> updateLaf(UIUtil.isUnderDarcula()));
  }

  @NotNull
  public static javafx.scene.paint.Color toFxColor(Color background) {
    double r = background.getRed() / 255.0;
    double g = background.getGreen() / 255.0;
    double b = background.getBlue() / 255.0;
    double a = background.getAlpha() / 255.0;
    return javafx.scene.paint.Color.color(r, g, b, a);
  }

  public void setBackground(Color background) {
    this.background = background;
    myPanelWrapper.setBackground(background);
    ApplicationManager.getApplication().invokeLater(() -> runFX(() -> {
      if (myPanel != null) {
        myPanel.getScene().setFill(toFxColor(background));
      }
    }));
  }

  private boolean isHtmlLoaded() {
    // return ourUrl.equals(myCefBrowser.getURL()); 99% match due to protocols
    return myCefBrowser.getURL().contains(JavaFxHtmlPanel.class.getSimpleName());
  }

  protected void registerListeners(@NotNull WebEngine engine) {
  }

  private static void runFX(@NotNull Runnable r) {
    IdeEventQueue.unsafeNonblockingExecute(r);
  }

  protected void runInPlatformWhenAvailable(@NotNull Runnable runnable) {
    //ApplicationManager.getApplication().assertIsDispatchThread();
    //if (myPanel == null) {
    //  myInitActions.add(runnable);
    //}
    //else {
    //  Platform.runLater(runnable);
    //}
  }

  @NotNull
  public JComponent getComponent() {
    return myPanelWrapper;
  }

  public void setHtml(@NotNull String html) {
    final String htmlToRender = prepareHtml(html);
    //runInPlatformWhenAvailable(() -> getWebViewGuaranteed().getEngine().loadContent(htmlToRender));
    if (!myIsCefBrowserCreated) {
      myHtml = htmlToRender;
    }
    else {
      myCefBrowser.loadString(htmlToRender, ourUrl);
    }
  }

  @NotNull
  protected String prepareHtml(@NotNull String html) {
    return html;
  }

  public void render() {
    //runInPlatformWhenAvailable(() -> {
    //  getWebViewGuaranteed().getEngine().reload();
    //  ApplicationManager.getApplication().invokeLater(myPanelWrapper::repaint);
    //});
  }

  @Nullable
  protected URL getStyle(boolean isDarcula) {
    return null;
  }

  private class JavaFXLafManagerListener implements LafManagerListener {
    @Override
    public void lookAndFeelChanged(@NotNull LafManager manager) {
      updateLaf(manager.getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo);
    }
  }

  private void updateLaf(boolean isDarcula) {
    URL styleUrl = getStyle(isDarcula);
    if (styleUrl == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(
      () -> runInPlatformWhenAvailable(
        () -> {
          final WebView webView = getWebViewGuaranteed();
          webView.getEngine().setUserStyleSheetLocation(styleUrl.toExternalForm());
        }
      ));
  }

  @Override
  public void dispose() {
    myCefBrowser.close(true);
    //runInPlatformWhenAvailable(
    //  () -> getWebViewGuaranteed().getEngine().load(null)
    //);
  }

  //public void addKeyListener(KeyListener l) {
  //  runInPlatformWhenAvailable(() -> myPanel.addKeyListener(l));
  //}


  @NotNull
  protected WebView getWebViewGuaranteed() {
    if (myWebView == null) {
      throw new IllegalStateException("WebView should be initialized by now. Check the caller thread");
    }
    return myWebView;
  }
}
