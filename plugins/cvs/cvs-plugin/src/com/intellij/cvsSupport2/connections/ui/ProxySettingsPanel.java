/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.connections.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ProxySettings;
import com.intellij.cvsSupport2.connections.CvsRootData;
import com.intellij.openapi.ui.InputException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class ProxySettingsPanel {
  private JPanel myPanel;
  private JTextField myProxyPort;
  private JTextField myProxyHost;
  private JCheckBox myUseProxy;
  private JRadioButton mySocks5;
  private JRadioButton mySocks4;
  private JRadioButton myHTTP;
  private JPasswordField myPassword;
  private JTextField myLogin;


  public ProxySettingsPanel() {
    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myHTTP);
    buttonGroup.add(mySocks4);
    buttonGroup.add(mySocks5);
    disableAll(false);

    myUseProxy.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (myUseProxy.isSelected()) {
          enableAll(false);
        }
        else {
          disableAll(false);
        }
      }
    });

  }

  public void disableAll(boolean disableUseProxyButton) {
    if (disableUseProxyButton) {
      myUseProxy.setEnabled(false);
    }
    myHTTP.setEnabled(false);
    mySocks4.setEnabled(false);
    mySocks5.setEnabled(false);
    myLogin.setEnabled(false);
    myPassword.setEnabled(false);
    myProxyHost.setEnabled(false);
    myProxyPort.setEnabled(false);
  }

  public void enableAll(boolean enableUseProxyButton) {
    if (enableUseProxyButton) {
      myUseProxy.setEnabled(true);
    }
    if (myUseProxy.isEnabled()) {
      myHTTP.setEnabled(true);
      mySocks4.setEnabled(true);
      mySocks5.setEnabled(true);
      myLogin.setEnabled(true);
      myPassword.setEnabled(true);
      myProxyHost.setEnabled(true);
      myProxyPort.setEnabled(true);
    }
  }

  public Component getPanel() {
    return myPanel;
  }

  public void updateFrom(CvsRootData cvsRootData) {
    myUseProxy.setSelected(true);
    myProxyHost.setText(cvsRootData.PROXY_HOST);
    myProxyPort.setText(cvsRootData.PROXY_PORT);
    myHTTP.setSelected(true);
  }

  public void updateFrom(ProxySettings proxy_settings) {
    myUseProxy.setSelected(proxy_settings.USE_PROXY);
    myProxyHost.setText(proxy_settings.PROXY_HOST);
    myProxyPort.setText(String.valueOf(proxy_settings.PROXY_PORT));

    if (proxy_settings.getType() == ProxySettings.HTTP) {
      myHTTP.setSelected(true);
    }
    else if (proxy_settings.getType() == ProxySettings.SOCKS4) {
      mySocks4.setSelected(true);
    }
    else {
      mySocks5.setSelected(true);
    }

    myLogin.setText(proxy_settings.getLogin());
    myPassword.setText(proxy_settings.getPassword());
    if (proxy_settings.USE_PROXY){
      enableAll(true);
    } else {
      disableAll(false);
    }
  }

  public boolean equalsTo(ProxySettings proxy_settings) {
    try {
      return myUseProxy.isSelected() == proxy_settings.USE_PROXY
             && myProxyHost.getText().equals(proxy_settings.PROXY_HOST)
             && getIntPortValue(myProxyPort.getText(), myProxyPort) == proxy_settings.PROXY_PORT
             && getSelectedType() == proxy_settings.getType()
             && proxy_settings.getLogin().equals(myLogin.getText())
             && proxy_settings.getPassword().equals(new String(myPassword.getPassword()));
    }
    catch (Exception e) {
      return false;
    }
  }

  private int getSelectedType() {
    if (myHTTP.isSelected()) {
      return ProxySettings.HTTP;
    }
    else if (mySocks4.isSelected()) {
      return ProxySettings.SOCKS4;
    }
    else {
      return ProxySettings.SOCKS5;
    }
  }

  private static int getIntPortValue(String text, JComponent component) {
    try {
      final int result = Integer.parseInt(text);
      if (result < 0) {
        throw new InputException(CvsBundle.message("error.message.invalid.port.value", text), component);
      }
      return result;
    }
    catch (NumberFormatException e) {
      throw new InputException(CvsBundle.message("error.message.invalid.port.value", text), component);
    }
  }

  public void saveTo(ProxySettings proxy_settings) {
    proxy_settings.USE_PROXY = myUseProxy.isSelected();
    proxy_settings.PROXY_HOST = myProxyHost.getText();
    proxy_settings.PROXY_PORT = getIntPortValue(myProxyPort.getText(), myProxyPort);
    proxy_settings.TYPE = getSelectedType();
    proxy_settings.LOGIN = myLogin.getText();
    proxy_settings.PASSWORD = new String(myPassword.getPassword());
  }


  public void disablePanel() {
    disableAll(true);
  }

  public void enablePanel() {
    enableAll(true);
    if (!myUseProxy.isSelected()){
      disableAll(false);
    }
  }
}