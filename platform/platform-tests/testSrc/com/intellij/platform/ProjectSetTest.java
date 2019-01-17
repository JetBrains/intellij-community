// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsCheckoutProcessor;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectSetProcessor;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class ProjectSetTest extends LightPlatformTestCase {

  private static String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "projectSet/";
  }

  public void testProjectSetReader() throws IOException {
    final Ref<List<? extends Pair<String, String>>> ref = Ref.create();
    PlatformTestUtil.registerExtension(ProjectSetProcessor.EXTENSION_POINT_NAME, new ProjectSetProcessor() {
      @Override
      public String getId() {
        return "test";
      }

      @Override
      public void processEntries(@NotNull List<? extends Pair<String, String>> entries, @NotNull Context context, @NotNull Runnable runNext) {
        ref.set(entries);
      }
    }, getTestRootDisposable());

    ProjectSetProcessor.Context context = new ProjectSetProcessor.Context();
    context.directory = getSourceRoot();
    readDescriptor(new File(getTestDataPath() + "descriptor.json"), context);

    List<? extends Pair<String, String>> entries = ref.get();
    assertEquals(2, entries.size());
    assertEquals("git://foo.bar", entries.get(0).getSecond());
    assertEquals("{\"foo\":\"bar\"}", entries.get(1).getSecond());
  }

  public void testVcsCheckoutProcessor() throws IOException {

    final List<Pair<String, String>> pairs = new ArrayList<>();
    PlatformTestUtil.registerExtension(VcsCheckoutProcessor.EXTENSION_POINT_NAME, new VcsCheckoutProcessor() {
      @NotNull
      @Override
      public String getId() {
        return "schema";
      }

      @Override
      public boolean checkout(@NotNull Map<String, String> parameters,
                              @NotNull VirtualFile parentDirectory, @NotNull String directoryName) {
        pairs.add(Pair.create(parameters.get("url"), directoryName));
        return true;
      }
    }, getTestRootDisposable());

    ProjectSetProcessor.Context context = new ProjectSetProcessor.Context();
    context.directoryName = "newDir";
    context.directory = getSourceRoot();
    readDescriptor(new File(getTestDataPath() + "vcs.json"), context);
    Collections.sort(pairs, (o1, o2) -> o2.first.compareTo(o1.first));
    assertEquals(Pair.create("schema://foo.bar/path", "test"), pairs.get(1));
    assertEquals(Pair.create("schema://foo.bar1/path1", "test/custom"), pairs.get(0));
  }

  public void testOpenProject() throws IOException {
    doOpenProject("project.json", "untitled");
  }

  public void testDefault() throws IOException {
    doOpenProject("default.json", "projectSet");
  }

  private static void doOpenProject(String file, final String projectName) throws IOException {
    ProjectSetProcessor.Context context = new ProjectSetProcessor.Context();
    context.directory = VfsUtil.findFileByIoFile(new File(getTestDataPath()), true);
    readDescriptor(new File(getTestDataPath() + file), context);
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    Project project = ContainerUtil.find(projects, project1 -> projectName.equals(project1.getName()));
    assertNotNull(project);
    ((ProjectManagerEx)ProjectManager.getInstance()).forceCloseProject(project, true);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  private static void readDescriptor(@NotNull File descriptor, @Nullable ProjectSetProcessor.Context context) throws IOException {
    try (InputStreamReader input = new InputStreamReader(new FileInputStream(descriptor), CharsetToolkit.UTF8_CHARSET)) {
      JsonElement parse = new JsonParser().parse(input);
      new ProjectSetReader().readDescriptor(parse.getAsJsonObject(), context);
    }
  }
}
