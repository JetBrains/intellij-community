/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 12/1/2015
 */
public class ExecutionNode extends CachingSimpleNode {

  @NotNull
  private ExecutionInfo myInfo;
  private final List<ExecutionNode> myNodes = ContainerUtil.newArrayList();

  protected ExecutionNode(Project project, @Nullable String workingDir) {
    super(project, null);
    this.myInfo = new ExecutionInfo(null, "--", workingDir);
  }

  public void add(ExecutionNode node) {
    myNodes.add(node);
    cleanUpCache();
  }

  @NotNull
  public ExecutionInfo getInfo() {
    return myInfo;
  }

  public void setInfo(@NotNull ExecutionInfo info) {
    myInfo = info;
  }

  @Override
  public String getName() {
    return myInfo.getDisplayName();
  }

  @Nullable
  public String getDuration() {
    if (myInfo.isRunning()) {
      final long duration = myInfo.getStartTime() == 0 ? 0 : System.currentTimeMillis() - myInfo.getStartTime();
      return "Running for " + StringUtil.formatDuration(duration);
    }
    else {
      return myInfo.isSkipped() ? null : StringUtil.formatDuration(myInfo.getEndTime() - myInfo.getStartTime());
    }
  }

  @Override
  public boolean isAutoExpandNode() {
    return true;
  }

  @Override
  protected ExecutionNode[] buildChildren() {
    return ContainerUtil.toArray(myNodes, new ExecutionNode[myNodes.size()]);
  }

  @Override
  protected void doUpdate() {
    setNameAndTooltip(getName(), null, myInfo.isUpToDate() ? "UP-TO-DATE" : null);
    setIcon(
      myInfo.isRunning() ? NodeProgressAnimator.getCurrentFrame() :
      myInfo.isFailed() ? AllIcons.Process.State.RedExcl :
      myInfo.isSkipped() ? AllIcons.Process.State.YellowStr :
      AllIcons.Process.State.GreenOK
    );
  }

  protected void setNameAndTooltip(String name, @Nullable String tooltip) {
    setNameAndTooltip(name, tooltip, (String)null);
  }

  protected void setNameAndTooltip(String name, @Nullable String tooltip, @Nullable String hint) {
    final SimpleTextAttributes textAttributes = getPlainAttributes();
    setNameAndTooltip(name, tooltip, textAttributes);
    if (!StringUtil.isEmptyOrSpaces(hint)) {
      addColoredFragment(" " + hint, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  protected void setNameAndTooltip(String name, @Nullable String tooltip, SimpleTextAttributes attributes) {
    clearColoredText();
    addColoredFragment(name, prepareAttributes(attributes));
    final String s = (tooltip != null ? tooltip + "\n\r" : "");
    getTemplatePresentation().setTooltip(s);
  }

  private static SimpleTextAttributes prepareAttributes(SimpleTextAttributes from) {
    return new SimpleTextAttributes(from.getBgColor(), from.getFgColor(), null, from.getStyle());
  }

  public String getMenuId() {
    return "RunContextGroup";
  }
}
