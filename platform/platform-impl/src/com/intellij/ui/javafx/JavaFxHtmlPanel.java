// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.javafx;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaFxHtmlPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(JavaFxHtmlPanel.class);
  // flag is reset after check
  public static final String JAVAFX_INITIALIZATION_INCOMPLETE_PROPERTY = "js.debugger.javafx.inititalization";
  @NotNull
  private final JPanel myPanelWrapper;
  @NotNull
  private final List<Runnable> myInitActions = new ArrayList<>();
  private final JavaFXLafManagerListener myLafManagerListener;
  @Nullable
  protected JFXPanel myPanel;
  @Nullable protected WebView myWebView;
  private Color background;
  @NotNull
  private static final AtomicBoolean HAS_WAITED_FOR_JAVAFX_ONCE = new AtomicBoolean(false);

  public JavaFxHtmlPanel() {
    PropertiesComponent.getInstance().setValue(JAVAFX_INITIALIZATION_INCOMPLETE_PROPERTY, true, false);
    ApplicationManager.getApplication().saveSettings();
    background = JBColor.background();
    myPanelWrapper = new JPanel(new BorderLayout());
    myPanelWrapper.setBackground(background);

    ApplicationManager.getApplication().invokeLater(() -> runFX(() -> {
      // before issuing PlatformImpl.startup(), check if the internal state of JavaFX initialization is stuck
      // a stuck JavaFX will happen when there is an exception thrown during the first PlatformImpl.startup()
      try {
        Field startupLatchField = PlatformImpl.class.getDeclaredField("startupLatch");
        startupLatchField.setAccessible(true);
        CountDownLatch startupLatch = (CountDownLatch)startupLatchField.get(null);

        Field initializedField = PlatformImpl.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        AtomicBoolean initialized = (AtomicBoolean)initializedField.get(null);

        if (startupLatch.getCount() == 1 && initialized.get()) {
          if (!HAS_WAITED_FOR_JAVAFX_ONCE.get()) {
            // wait a bit to allow initialization to finish, but only once
            try {
              startupLatch.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException ignored) {
            }
            HAS_WAITED_FOR_JAVAFX_ONCE.set(true);
          }
          if (startupLatch.getCount() == 1) {
            LOG.warn("JavaFX is stuck");
            // previous initialization failed, JavaFX not available
            return;
          }
        }
      }
      catch (NoSuchFieldException | IllegalAccessException ex) {
        LOG.error("can't read state of PlatformImpl", ex);
      }
      try {
        PlatformImpl.startup(() -> {
          PropertiesComponent.getInstance().setValue(JAVAFX_INITIALIZATION_INCOMPLETE_PROPERTY, false, false);
          myWebView = new WebView();
          myWebView.setContextMenuEnabled(false);
          myWebView.setZoom(JBUIScale.scale(1.f));

          final WebEngine engine = myWebView.getEngine();
          registerListeners(engine);
          engine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
                                                               if (newValue == Worker.State.RUNNING) {
                                                                 WebPage page = Accessor.getPageFor(engine);
                                                                 page.setBackgroundColor(background.getRGB());
                                                               }
                                                             }
          );

          javafx.scene.paint.Color fxColor = toFxColor(background);
          final Scene scene = new Scene(myWebView, fxColor);

          ApplicationManager.getApplication().invokeLater(() -> runFX(() -> {
            try {
              myPanel = new JFXPanelWrapper();

              Platform.runLater(() -> myPanel.setScene(scene));

              setHtml("");
              for (Runnable action : myInitActions) {
                Platform.runLater(action);
              }
              myInitActions.clear();

              myPanelWrapper.add(myPanel, BorderLayout.CENTER);
              myPanelWrapper.repaint();
            }
            catch (Throwable e) {
              LOG.warn("can't initialize JFXPanelWrapper", e);
            }
          }));
        });
      }
      catch (Throwable e) {
        LOG.warn("can't start javaFX", e);
      }
    }));

    myLafManagerListener = new JavaFXLafManagerListener();
    LafManager.getInstance().addLafManagerListener(myLafManagerListener);
    runInPlatformWhenAvailable(() -> updateLaf(UIUtil.isUnderDarcula()));
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
    runInPlatformWhenAvailable(
      () -> getWebViewGuaranteed().getEngine().load(null)
    );
    LafManager.getInstance().removeLafManagerListener(myLafManagerListener);
  }


  @NotNull
  protected WebView getWebViewGuaranteed() {
    if (myWebView == null) {
      throw new IllegalStateException("WebView should be initialized by now. Check the caller thread");
    }
    return myWebView;
  }
}
