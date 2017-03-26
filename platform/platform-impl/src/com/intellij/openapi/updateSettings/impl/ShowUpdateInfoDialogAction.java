/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileTextField;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.JdomKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

public class ShowUpdateInfoDialogAction extends AnAction {

  public ShowUpdateInfoDialogAction() {
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    String title = "Updates.xml <channel> Text";
    JBList list = new JBList("Manual Text", "Sample Text (short)", "Sample Text (long)");
    JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setTitle(title)
      .setFilteringEnabled(o -> (String)o)
      .setItemChoosenCallback(() -> {
        int index = list.getSelectedIndex();
        Pair<String, VirtualFile> info = index == 0 ? getUserText(project, title) : Pair.create(getXML(index == 2), null);
        showDialog(info.first, info.second);
      })
      .createPopup()
      .showCenteredInCurrentWindow(project);
  }

  @NotNull
  private static Pair<String, VirtualFile> getUserText(@NotNull Project project, @NotNull String title) {
    JTextArea textArea = new JTextArea(10, 50);
    UIUtil.addUndoRedoActions(textArea);
    textArea.setWrapStyleWord(true);
    textArea.setLineWrap(true);
    JPanel panel = new JPanel(new BorderLayout(0, 10));
    panel.add(ScrollPaneFactory.createScrollPane(textArea), BorderLayout.CENTER);
    Disposable disposable = Disposer.newDisposable();
    FileTextField fileField = FileChooserFactory.getInstance().createFileTextField(BrowseFilesListener.SINGLE_FILE_DESCRIPTOR, disposable);
    TextFieldWithBrowseButton fileCompo = new TextFieldWithBrowseButton(fileField.getField());
    FileChooserDescriptor fileDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    fileCompo.addBrowseFolderListener("Patch File", "Patch file", project, fileDescriptor);
    panel.add(LabeledComponent.create(fileCompo, "Patch file:"), BorderLayout.SOUTH);
    DialogBuilder builder = new DialogBuilder(project);
    builder.addDisposable(disposable);
    builder.setCenterPanel(panel);
    builder.setPreferredFocusComponent(textArea);
    builder.setTitle(title);
    builder.addOkAction();
    builder.addCancelAction();
    return builder.showAndGet() ? Pair.create(textArea.getText(), fileField.getSelectedFile()) : Pair.empty();
  }

  protected void showDialog(@Nullable String text, @Nullable VirtualFile patchFile) {
    String trim = StringUtil.trim(text);
    if (StringUtil.isEmpty(trim)) return;

    Element element;
    try {
      element = JdomKt.loadElement(trim);
      if (!"channel".equals(element.getName())) return;
    }
    catch (Exception ex) {
      Logger.getInstance(ShowUpdateInfoDialogAction.class).error(ex);
      return;
    }
    UpdateChannel channel = new UpdateChannel(element);
    BuildInfo newBuild = ContainerUtil.getFirstItem(channel.getBuilds());
    if (newBuild == null) return;
    PatchInfo patch = ContainerUtil.getFirstItem(newBuild.getPatches());
    UpdateInfoDialog dialog = new UpdateInfoDialog(channel, newBuild, patch, true, UpdateSettings.getInstance().canUseSecureConnection(),
                                                   Collections.emptyList(), Collections.emptyList()) {
      @NotNull
      @Override
      File doDownloadPatch(@NotNull ProgressIndicator indicator) throws IOException {
        File file = patchFile == null ? null : VfsUtilCore.virtualToIoFile(patchFile);
        return file != null ? file : super.doDownloadPatch(indicator);
      }
    };
    dialog.setTitle("[TEST] " + dialog.getTitle());
    dialog.show();
  }


  private static final String PARAGRAPH = "    <li><strong>Refactoring to Java 8</strong>: more powerful inspections.</li>\n" +
                                          "    <li><strong>JVM Debugger</strong>: Class-level Watches; the JVM Memory View plugin.</li>\n" +
                                          "    <li><strong>User Interface</strong>: Parameter Hints; Semantic Highlighting; flat file icons.</li>\n" +
                                          "    <li><strong>Build Tools</strong>: Delegate IDE build/run actions to Gradle; Composite <br>\n" +
                                          "        Builds support; Polyglot Maven.</li>\n" +
                                          "    <li><strong>Scala</strong>: scala.meta and Scala.js</li>\n" +
                                          "    <li><strong>JavaScript</strong>: refactoring to ECMAScript 6; Flow-based inspections; <br>\n" +
                                          "        React Native debugger, Protractor, Stylelint, PostCSS, and more.</li>\n" +
                                          "    <li><strong>VCS</strong>: Faster and more ergonomic Log for Git/Mercurial; automatic <br>\n" +
                                          "        resolving conflicts; managing Git remotes.</li>\n" +
                                          "    <li><strong>Android</strong>: Blueprint; Constraint Layout, APK Analyzer, and better Instant Run.</li>\n" +
                                          "    <li><strong>Database</strong>: editing multiple cells at once; submit changes in bulk; <br>\n" +
                                          "        find usages of objects within the source code of other objects.</li>\n";

  private static final String CHANNEL_XML_START = "<!DOCTYPE products SYSTEM \"updates.dtd\">\n" +
                                                  "<channel id=\"IDEA_EAP\" name=\"IntelliJ IDEA EAP\" status=\"eap\"\n" +
                                                  "             url=\"https://confluence.jetbrains.com/display/IDEADEV/IDEA+2016.3+EAP\"\n" +
                                                  "             feedback=\"http://youtrack.jetbrains.net\"\n" +
                                                  "             majorVersion=\"2016\" licensing=\"eap\">\n" +
                                                  "      <build number=\"163.7743.44\" version=\"2016.3\" releaseDate=\"20161122\">\n" +
                                                  "        <message><![CDATA[\n" +
                                                  "        <p>Please meet IntelliJ IDEA 2016.3, the third update planned for 2016!</p>\n" +
                                                  "\n" +
                                                  "<p>Visit <a href=\"https://www.jetbrains.com/idea/whatsnew/?landing\">What's New</a> page for a full list of new features and an overview video.</p>\n" +
                                                  "\n" +
                                                  "<p>And here're the highlights:</p>\n" +
                                                  "\n" +
                                                  "<ul>\n";

  private static final String CHANNEL_XML_END = "</ul>\n" +
                                                "]]></message>\n" +
                                                "        <button name=\"Download\" url=\"https://www.jetbrains.com/idea/download/\" download=\"true\"/>\n" +
                                                "        <button name=\"What's New\" url=\"https://www.jetbrains.com/idea/whatsnew/?landing\"/>\n" +
                                                "        <patch from=\"163.7743.17\" size=\"from 2 to 18\"/>\n" +
                                                "        <patch from=\"163.7743.37\" size=\"from 2 to 18\"/>\n" +
                                                "      </build>\n" +
                                                "      <build number=\"162.2228.15\" version=\"2016.2.5\" releaseDate=\"20160712\">\n" +
                                                "        <message><![CDATA[\n" +
                                                "        <p>The <strong>IntelliJ IDEA 2016.2.5</strong> update is available. <br>\n" +
                                                "    Apart from bugfixes, the update brings support for <strong>macOS Sierra</strong>.</p>\n" +
                                                "]]></message>\n" +
                                                "        <button name=\"Download\" url=\"https://www.jetbrains.com/idea/download/\" download=\"true\"/>\n" +
                                                "        <button name=\"Release Notes\" url=\"https://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+2016.2.5+Release+Notes\"/>\n" +
                                                "        <patch from=\"162.2032.8\" size=\"from 1 to 13\"/>\n" +
                                                "        <patch from=\"162.2228.14\" size=\"from 21 to 53\"/>\n" +
                                                "        <patch from=\"162.1812.17\" size=\"from 21 to 55\"/>\n" +
                                                "        <patch from=\"162.1628.40\" size=\"from 21 to 59\"/>\n" +
                                                "        <patch from=\"162.1447.26\" size=\"from 21 to 67\"/>\n" +
                                                "        <patch from=\"162.1121.32\" size=\"from 33 to 80\"/>\n" +
                                                "      </build>\n" +
                                                "      <build number=\"162.1121.32\" version=\"2016.2\" releaseDate=\"20160712\">\n" +
                                                "        <message><![CDATA[\n" +
                                                "        <p>Welcome <strong>IntelliJ IDEA 2016.2</strong>, a second update planned for this year.<br>\n" +
                                                "    The update brings lots of new features and improvements across the <br>\n" +
                                                "    built-in tools, UI, and support for languages and frameworks.\n" +
                                                "</p>\n" +
                                                "\n" +
                                                "<p>Learn more about the update by reading the <a href=\"http://blog.jetbrains.com/idea/2016/07/intellij-idea-2016-2-is-here/\">blog post</a>.</p>\n" +
                                                "]]></message>\n" +
                                                "        <button name=\"What's New\" url=\"https://www.jetbrains.com/idea/whatsnew/\"/>\n" +
                                                "        <button name=\"Download\" url=\"https://www.jetbrains.com/idea/download/\" download=\"true\"/>\n" +
                                                "        <patch from=\"162.1121.10\" size=\"from 1 to 12\"/>\n" +
                                                "      </build>\n" +
                                                "      <build number=\"145.2070\" version=\"2016.1.4\" releaseDate=\"20160316\">\n" +
                                                "        <message><![CDATA[\n" +
                                                "        <p>IntelliJ IDEA 2016.1.4 build 145.2070 is available with important bugfixes.</p>\n" +
                                                "]]></message>\n" +
                                                "        <button name=\"Release Notes\" url=\"https://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+2016.1.4+Release+Notes\"/>\n" +
                                                "        <button name=\"Download\" url=\"https://confluence.jetbrains.com/display/IntelliJIDEA/Previous+IntelliJ+IDEA+Releases\"\n" +
                                                "                download=\"true\"/>\n" +
                                                "        <patch from=\"145.1617\" size=\"from 42 to 58\"/>\n" +
                                                "      </build>\n" +
                                                "    </channel>\n";

  private static String getXML(boolean bigData) {
    StringBuilder sb = new StringBuilder(CHANNEL_XML_START);
    int count = bigData ? 4 : 1;
    for (int i = 0; i < count; i++) {
      sb.append(PARAGRAPH);
    }
    sb.append(CHANNEL_XML_END);
    return sb.toString();
  }
}
