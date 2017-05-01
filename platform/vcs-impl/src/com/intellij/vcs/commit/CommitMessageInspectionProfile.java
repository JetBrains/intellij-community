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
package com.intellij.vcs.commit;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.intellij.codeInspection.InspectionProfileEntry.getShortName;
import static com.intellij.util.ObjectUtils.notNull;
import static java.util.stream.Collectors.toList;

@State(name = "CommitMessageInspectionProfile", storages = @Storage("vcs.xml"))
public class CommitMessageInspectionProfile extends InspectionProfileImpl
  implements PersistentStateComponent<CommitMessageInspectionProfile.State> {

  public static final String PROFILE_NAME = "Commit Dialog";
  public static final InspectionProfileImpl DEFAULT =
    new InspectionProfileImpl(PROFILE_NAME, new CommitMessageInspectionToolSupplier(), (InspectionProfileImpl)null);

  @NotNull private final Project myProject;
  @NotNull private State myState = new State();

  public CommitMessageInspectionProfile(@NotNull Project project) {
    super(PROFILE_NAME, new CommitMessageInspectionToolSupplier(), DEFAULT);
    myProject = project;
  }

  @NotNull
  public static CommitMessageInspectionProfile getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CommitMessageInspectionProfile.class);
  }

  public static int getBodyRightMargin(@NotNull Project project) {
    return getInstance(project).getBodyRightMargin();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public int getBodyRightMargin() {
    InspectionToolWrapper toolWrapper = getInspectionTool(getShortName(BodyLimitInspection.class.getSimpleName()), myProject);

    return ((BodyLimitInspection)notNull(toolWrapper).getTool()).RIGHT_MARGIN;
  }

  @Override
  protected boolean forceInitInspectionTools() {
    return true;
  }

  @NotNull
  @Override
  @Transient
  public String getName() {
    return super.getName();
  }

  @NotNull
  @Override
  public State getState() {
    Element element = newProfileElement();
    writeExternal(element);
    myState.myProfile = element;

    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    readExternal(state.myProfile);
  }

  public static class State {
    @Tag(PROFILE)
    public Element myProfile = newProfileWithVersionElement();
  }

  @NotNull
  private static Element newProfileElement() {
    return new Element(PROFILE);
  }

  @NotNull
  private static Element newProfileWithVersionElement() {
    Element result = newProfileElement();
    writeVersion(result);
    return result;
  }

  private static class CommitMessageInspectionToolSupplier implements Supplier<List<InspectionToolWrapper>> {
    @NotNull
    @Override
    public List<InspectionToolWrapper> get() {
      return Stream.of(new SubjectBodySeparationInspection(), new SubjectLimitInspection(), new BodyLimitInspection(),
                       new CommitMessageSpellCheckingInspection())
        .map(LocalInspectionToolWrapper::new)
        .collect(toList());
    }
  }
}
