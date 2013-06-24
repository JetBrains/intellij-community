/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.cvsSupport2.config.ProxySettings;
import com.intellij.cvsSupport2.connections.CvsRootData;
import com.intellij.cvsSupport2.ui.FormUtils;

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
    final ButtonGroup buttonGroup = new ButtonGroup();
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

  public boolean equalsTo(ProxySettings proxySettings) {
    if (!myUseProxy.isSelected()) {
      return !proxySettings.USE_PROXY;
    }
    return myUseProxy.isSelected() == proxySettings.USE_PROXY
           && myProxyHost.getText().equals(proxySettings.PROXY_HOST)
           && FormUtils.getPositiveIntFieldValue(myProxyPort, false, false, 0xFFFF) == proxySettings.PROXY_PORT
           && getSelectedType() == proxySettings.getType()
           && myLogin.getText().equals(proxySettings.getLogin())
           && new String(myPassword.getPassword()).equals(proxySettings.getPassword());
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

  public void saveTo(ProxySettings proxySettings) {
    proxySettings.USE_PROXY = myUseProxy.isSelected();
    proxySettings.PROXY_HOST = FormUtils.getFieldValue(myProxyHost, proxySettings.USE_PROXY);
    proxySettings.PROXY_PORT = FormUtils.getPositiveIntFieldValue(myProxyPort, true, false, 0xFFFF);
    proxySettings.TYPE = getSelectedType();
    proxySettings.LOGIN = myLogin.getText();
    proxySettings.PASSWORD = new String(myPassword.getPassword());
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