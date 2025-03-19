// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.scale.TestScaleHelper;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLatch;
import static org.junit.Assert.fail;

public class JBCefFpsTest {
  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
    TestScaleHelper.setSystemProperty("jcef.debug.collect_fps_stats", "true");
  }

  public void testPageScrolling() {
    _testScrollingImpl(600, 1000, 5, 20);
  }

  private void _testScrollingImpl(int w, int h, int speed, int delay) {
    String htmlPath = PlatformTestUtil.getPlatformTestDataPath() + "ui/jcef/long_page.html";
    String url = "file://" + htmlPath;
    _testImpl(w, h, (f)->{
      try {
        Robot robot = new Robot();
        robot.setAutoDelay(0);
        Rectangle r = f.getBounds();
        robot.mouseMove((int)r.getCenterX(), (int)r.getCenterY());
        robot.delay(500);
        final long startMs = System.currentTimeMillis();
        while (System.currentTimeMillis() - startMs < 20*1000) {
          robot.mouseWheel(-speed);
          robot.delay(delay);
        }
      } catch (AWTException e) {
        throw new RuntimeException(e);
      }
    }, url, String.format("test_scroll_fps_%dx%d_speed%d.csv", w, h, speed));
  }

  public void testAnimatedSvgLarge() {
    _testSvgImpl(2500, 1600);
  }

  public void testAnimatedSvg() {
    _testSvgImpl(1400, 800);
  }

  private void _testSvgImpl(int w, int h) {
    String svgPath = PlatformTestUtil.getPlatformTestDataPath() + "ui/jcef/animated.svg";
    String url = "file://" + svgPath;
    _testImpl(w, h, (f)->{
      try {
        Thread.sleep(20*1000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }, url, String.format("test_svg_fps_%dx%d.csv", w, h));
  }

  private void _testImpl(int w, int h, Consumer<JFrame> testActor, String url, String outFilename) {
    final CountDownLatch loaded = new CountDownLatch(1);
    JFrame[] f = new JFrame[]{null};
    invokeAndWaitForLatch(loaded, "create browser and loadUrl -> wait onLoadEnd", () -> {
      JFrame frame = f[0] = new JFrame(String.format("Test fps %dx%d (%s)", w, h, url));
      frame.setSize(w, h);
      frame.setLocationRelativeTo(null);

      JBCefBrowser browser = new JBCefBrowser(new JBCefBrowserBuilder().setOffScreenRendering(true));
      browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
        @Override
        public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
          browser.getJBCefClient().removeLoadHandler(this, cefBrowser);
          loaded.countDown();
        }
      }, browser.getCefBrowser());

      var comp = browser.getComponent();
      comp.setPreferredSize(new Dimension(w, h));
      frame.add(comp, BorderLayout.CENTER);
      frame.pack();
      frame.setVisible(true);
      browser.loadURL(url);
    });

    // Start fps meter
    String FPS_METER_ID = RegistryManager.getInstance().get("ide.browser.jcef.osr.measureFPS.id").asString();
    final JBCefFpsMeter fpsMeter = JBCefFpsMeter.get(FPS_METER_ID);
    if (fpsMeter == null) {
      fail("Can't get FPS meter instance.");
      return;
    }
    fpsMeter.setActive(true);
    testActor.accept(f[0]);
    fpsMeter.setActive(false);

    // Write collected data and dispose frame.
    try {
      fpsMeter.writeStats(new PrintStream(outFilename));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    f[0].dispose();
  }
}
