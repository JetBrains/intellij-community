// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.EdtDataContext;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.UnlockOption;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.SystemNotifications;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

public class IdeUiServiceImpl extends IdeUiService {
  @Override
  public void revealFile(File file) {
    RevealFileAction.openFile(file);
  }

  @Override
  public UnlockOption askForUnlock(@NotNull Project project, List<? extends VirtualFile> files) {
    NonProjectFileWritingAccessDialog dialog = new NonProjectFileWritingAccessDialog(project, files);
    if (!dialog.showAndGet()) return null;
    return dialog.getUnlockOption();
  }

  @Override
  public boolean isFileRecentlyChanged(Project project, VirtualFile file) {
    IdeDocumentHistoryImpl documentHistory = (IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(project);
    return documentHistory.isRecentlyChanged(file);
  }

  @Override
  public void logUsageEvent(Class<?> clazz, String groupId, String eventId) {
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfo(clazz);
    String factoryClass = pluginInfo.isSafeToReport() ? clazz.getName() : "third.party";
    FeatureUsageData data = new FeatureUsageData().addData("factory", factoryClass).addPluginInfo(pluginInfo);
    FUCounterUsageLogger.getInstance().logEvent(groupId, eventId, data);
  }

  @Override
  public void systemNotify(String title, String text) {
    SystemNotifications.getInstance().notify("SessionLogger", title, StringUtil.stripHtml(text, true));
  }

  @Override
  public DataContext createUiDataContext(Component component) {
    return new EdtDataContext(component);
  }

  @Override
  public Component getComponentFromRecentMouseEvent() {
    return SwingHelper.getComponentFromRecentMouseEvent();
  }

  @Override
  public void browse(URL url) {
    BrowserUtil.browse(url);
  }

  @Override
  public void browse(String url) {
    BrowserUtil.browse(url);
  }

  @Override
  public void performActionDumbAwareWithCallbacks(AnAction action,
                                                  AnActionEvent event) {
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event);
    }
  }

  @Override
  public void notifyByBalloon(Project project,
                              String toolWindowId,
                              MessageType messageType,
                              String title, String fullMessage, String description,
                              Icon icon, HyperlinkListener listener) {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (toolWindowManager.canShowNotification(toolWindowId)) {
      //noinspection SSBasedInspection
      toolWindowManager.notifyByBalloon(toolWindowId, MessageType.ERROR, fullMessage, icon, listener);
    }
    else {
      Messages.showErrorDialog(project, UIUtil.toHtml(description), title);
    }
  }

  @Override
  public URLConnection openHttpConnection(String url) throws IOException {
    return HttpConfigurable.getInstance().openConnection(url);
  }

  @Override
  public SSLSocketFactory getSslSocketFactory() {
    return CertificateManager.getInstance().getSslContext().getSocketFactory();
  }

  @Override
  public boolean isUseSafeWrite() {
    return GeneralSettings.getInstance().isUseSafeWrite();
  }

  @Override
  public VirtualFile[] chooseFiles(FileChooserDescriptor descriptor,
                                   Project project, VirtualFile toSelect) {
    return FileChooser.chooseFiles(descriptor, project, toSelect);
  }

  @Override
  public SSLContext getSslContext() {
    return CertificateManager.getInstance().getSslContext();
  }

  @Override
  public String getProxyLogin() {
    return HttpConfigurable.getInstance().getProxyLogin();
  }

  @Override
  public String getPlainProxyPassword() {
    return HttpConfigurable.getInstance().getPlainProxyPassword();
  }

  @Override
  public boolean isProxyAuth() {
    return HttpConfigurable.getInstance().PROXY_AUTHENTICATION;
  }

  @Override
  public List<Proxy> getProxyList(URL url) {
    return CommonProxy.getInstance().select(url);
  }
}
