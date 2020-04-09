// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.jcef;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefCookieManager;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author tav
 */
public class WebBrowser extends AnAction implements DumbAware {
  private static final String URL = "http://maps.google.com";
  private static final String myTitle = "Web Browser - JCEF";
  private static final String myShowCookiesButtonText = "Show Cookies";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) return;

    if (!JBCefApp.isEnabled()) {
      JBPopupFactory.getInstance().createComponentPopupBuilder(
        new JTextArea("Set the reg key to enable JCEF:\n\"ide.browser.jcef.enabled=true\""), null).
        setTitle("JCEF web browser is not available").
        createPopup().
        showInCenterOf(activeFrame);
      return;
    }

    Rectangle bounds = activeFrame.getGraphicsConfiguration().getBounds();

    final JFrame frame = new IdeFrameImpl();
    frame.setTitle(myTitle);
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    frame.setBounds(bounds.width / 4, bounds.height / 4, bounds.width / 2, bounds.height / 2);
    frame.setLayout(new BorderLayout());

    final JBCefBrowser myJBCefBrowser = new JBCefBrowser(URL);
    final JBCefCookieManager myJBCefCookieManager = myJBCefBrowser.getJBCefCookieManager();
    final CookieManagerDialog myCookieManagerDialog = new CookieManagerDialog(frame);

    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        Disposer.dispose(myJBCefBrowser);
      }
    });
    myJBCefBrowser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
        myCookieManagerDialog.setVisible(false);
      }
    }, myJBCefBrowser.getCefBrowser());

    frame.add(myJBCefBrowser.getComponent(), BorderLayout.CENTER);

    final JTextField myUrlBar = new JTextField(URL);
    myUrlBar.addActionListener(event -> myJBCefBrowser.loadURL(myUrlBar.getText()));
    frame.add(myUrlBar, BorderLayout.NORTH);

    JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));

    final JButton myShowCookies = new JButton(myShowCookiesButtonText);
    myShowCookies.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCookieManagerDialog.setVisible(true);
        myCookieManagerDialog.update(myJBCefCookieManager.getCookies());
      }
    });
    controlPanel.add(myShowCookies);
    frame.add(controlPanel, BorderLayout.SOUTH);

    frame.setVisible(true);
  }
}
