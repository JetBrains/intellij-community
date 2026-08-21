// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.actions.CollectZippedLogsAction;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.UnlockOption;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.ui.SystemNotifications;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IOExceptionDialog;
import com.intellij.util.net.JdkProxyProvider;
import com.intellij.util.net.ProxyCredentialStore;
import com.intellij.util.net.ProxySettings;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.system.OS;
import com.intellij.util.ui.SwingHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.swing.JComponent;
import java.awt.Component;
import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.List;

@ApiStatus.Internal
public class IdeUiServiceImpl extends IdeUiService {
  @Override
  public void revealFile(Path file) {
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
    return IdeDocumentHistory.getInstance(project) instanceof IdeDocumentHistoryImpl impl && impl.isRecentlyChanged(file);
  }

  @Override
  public void logIdeScriptUsageEvent(Class<?> clazz) {
    IdeScriptEngineUsageCollector.logUsageEvent(clazz);
  }

  @Override
  public void systemNotify(@NlsContexts.SystemNotificationTitle String title, @NlsContexts.SystemNotificationText String text) {
    SystemNotifications.getInstance().notify("SessionLogger", title, StringUtil.stripHtml(text, true));
  }

  @Override
  public @NotNull DataContext createUiDataContext(Component component) {
    return Utils.createAsyncDataContext(component);
  }

  @Override
  public @NotNull DataContext createAsyncDataContext(@NotNull DataContext dataContext) {
    return Utils.createAsyncDataContext(dataContext);
  }

  @Override
  public @NotNull DataContext createCustomizedDataContext(@NotNull DataContext dataContext, @NotNull DataProvider dataProvider) {
    return Utils.createAsyncDataContext(dataContext, dataProvider);
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
  public void performAction(@NotNull AnAction action, @NotNull AnActionEvent event) {
    ActionUtil.performAction(action, event);
  }

  @Override
  public URLConnection openHttpConnection(String url) throws IOException {
    return HttpConfigurable.getInstance().openConnection(url);
  }

  @Override
  public VirtualFile[] chooseFiles(FileChooserDescriptor descriptor, Project project, VirtualFile toSelect) {
    return FileChooser.chooseFiles(descriptor, project, toSelect);
  }

  @Override
  public VirtualFile chooseFile(FileChooserDescriptor descriptor, JComponent component, Project project, VirtualFile dir) {
    return FileChooser.chooseFile(descriptor, component, project, dir);
  }

  @Override
  public SSLContext getSslContext() {
    return CertificateManager.getInstance().getSslContext();
  }

  @Override
  public @Nullable Pair<String, char[]> getProxyCredentials() {
    var credentials = ProxyCredentialStore.getInstance().getCredentials(ProxySettings.getInstance().getProxyConfiguration());
    return credentials != null ? new Pair<>(credentials.getUserName(), credentials.getPassword() != null ? credentials.getPassword().toCharArray() : null) : null;
  }

  @Override
  public @NotNull List<Proxy> getProxyList(URI uri) {
    return JdkProxyProvider.getInstance().getProxySelector().select(uri);
  }

  @Override
  public void prepareURL(String url) throws IOException {
    HttpConfigurable.getInstance().prepareURL(url);
  }

  @Override
  public void showRefactoringMessageDialog(
    @NlsContexts.DialogTitle String title,
    @NlsContexts.DialogMessage String message,
    String helpTopic,
    String iconId,
    boolean showCancelButton,
    Project project
  ) {
    RefactoringMessageDialog dialog = new RefactoringMessageDialog(title, message, helpTopic, iconId, showCancelButton, project);
    dialog.show();
  }

  @Override
  public void showErrorHint(Editor editor, @NlsContexts.HintText String message) {
    HintManager.getInstance().showErrorHint(editor, message);
  }

  @Override
  public boolean showErrorDialog(@NlsContexts.DialogTitle String title, @NlsContexts.DetailedDescription String message) {
    return IOExceptionDialog.showErrorDialog(title, message);
  }

  @Override
  public @Nullable String getMacOsNetworkSolutionMessage(@NotNull Throwable error, boolean full) {
    if (
      OS.CURRENT == OS.macOS && OS.CURRENT.isAtLeast(15, 0) &&
      (error instanceof NoRouteToHostException || error.getCause() instanceof NoRouteToHostException)
    ) {
      return IdeCoreBundle.message(full ? "mac15.local.network.issue.full.message" : "mac15.local.network.issue.message");
    }
    else return null;
  }

  @Override
  public void showProxyAuthNotification() {
    JdkProxyProvider.showProxyAuthNotification();
  }

  @Override
  public void invokeLogCollectionAction(@Nullable Project project) {
    new CollectZippedLogsAction().perform(project);
  }
}
