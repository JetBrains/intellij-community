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
import com.intellij.ui.jcef.JBCefCookie;
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
import java.util.List;

/**
 * @author tav
 */
public class WebBrowser extends AnAction implements DumbAware {
  private static final String URL = "http://maps.google.com";
  private static final String myTitle = "Web Browser - JCEF";
  private static final String myCookieManagerText = "Cookie Manager";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) return;

    if (!JBCefApp.isSupported()) {
      JBPopupFactory.getInstance().createComponentPopupBuilder(
        new JTextArea("Set the reg key to enable JCEF:\n\"ide.browser.jcef.enabled=true\""), null).
        setTitle("JCEF Web Browser Is not Supported").
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
    final CookieManagerDialog myCookieManagerDialog = new CookieManagerDialog(frame, myJBCefBrowser);

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

    final JButton myShowCookieManagerButton = new JButton(myCookieManagerText);
    myShowCookieManagerButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCookieManagerDialog.setVisible(true);
        List<JBCefCookie> cookies = myJBCefBrowser.getJBCefCookieManager().getCookies();
        if (cookies != null) {
          myCookieManagerDialog.update(cookies);
        }
      }
    });
    controlPanel.add(myShowCookieManagerButton);

    frame.add(controlPanel, BorderLayout.SOUTH);

    JMenuBar menuBar = new JMenuBar();
    frame.setJMenuBar(menuBar);
    JMenu menu = new JMenu("Tools");
    menuBar.add(menu);
    JMenuItem menuItem = new JMenuItem("Load HTML with URL");
    menu.add(menuItem);
    menuItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JDialog dialog = new JDialog(frame, "Load HTML with URL");
        JPanel panel = new JPanel();
        JTextField url = new JTextField("file://");
        JTextArea html = new JTextArea("<html>\n</html>");

        JPanel buttonPanel = new JPanel();
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(ae -> dialog.dispose());
        JButton loadButton = new JButton("Load");
        loadButton.addActionListener(ae -> {
          dialog.dispose();
          myUrlBar.setText(url.getText());
          SwingUtilities.invokeLater(() -> myJBCefBrowser.loadHTML(html.getText(), url.getText()));
        });
        buttonPanel.add(cancelButton);
        buttonPanel.add(loadButton);

        dialog.add(panel);
        panel.setLayout(new BorderLayout());
        panel.add(url, BorderLayout.NORTH);
        panel.add(html, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setSize(640, 480);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
      }
    });

    frame.setVisible(true);
  }
}
