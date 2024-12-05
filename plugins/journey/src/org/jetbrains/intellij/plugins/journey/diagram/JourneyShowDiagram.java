package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagnostic.Checks;
import com.intellij.diagram.DiagramDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.uml.UmlVirtualFileSystem;
import com.intellij.uml.core.actions.ShowDiagram;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

import static org.jetbrains.intellij.plugins.journey.diagram.JourneyShowDiagram.JourneyShowDiagramFromNewLocalFile.showDiagramFromFile;

public class JourneyShowDiagram extends ShowDiagram {

  public static final boolean USE_LOCAL_FILE_STORAGE = true;

  public void showDiagram(Project project, PsiElement file) {
    if (USE_LOCAL_FILE_STORAGE) {
      showDiagramFromFile(project, file);
    }
    else {
      DiagramSeed seed =
        createSeed(file.getProject(), JourneyDiagramProvider.getInstance(), new JourneyNodeIdentity(file), Collections.emptyList());
      showUnderProgress(seed, new RelativePoint(new Point()));
    }
  }

  public static VirtualFile getUmlVirtualFile() {
    final var url = UmlVirtualFileSystem.PROTOCOL_PREFIX + "JOURNEY/" + System.currentTimeMillis();
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  @Override
  @RequiresReadLock
  protected @NotNull UmlVirtualFileSystem.UmlVirtualFile initializeDiagramVirtualFile(@NotNull DiagramSeed seed) {
    final var element = seed.getOriginalElement();
    final var virtualFile = getUmlVirtualFile();
    Checks.check(virtualFile instanceof UmlVirtualFileSystem.UmlVirtualFile);

    final var file = (UmlVirtualFileSystem.UmlVirtualFile)virtualFile;
    if (file.getProject() == null) file.setProject(seed.getProject());
    file.putUserData(DiagramDataKeys.ORIGINAL_ELEMENT, element);
    return file;
  }

  public static final class JourneyShowDiagramFromNewLocalFile {
    private static final Logger LOG = Logger.getInstance(JourneyShowDiagram.class);

    public static final String ORIGINAL_ELEMENT_PLACEHOLDER = "$$ORIGINAL_ELEMENT$$";

    public static final String INITIAL = """
      <?xml version="1.0" encoding="UTF-8"?>
      <Diagram>
        <ID>JOURNEY</ID>
        <OriginalElement>$$ORIGINAL_ELEMENT$$</OriginalElement>
        <nodes>
          <node x="0.0" y="0.0">$$ORIGINAL_ELEMENT$$</node>
        </nodes>
        <notes />
        <edges>
        </edges>
        <settings layout="Hierarchic" zoom="1.0" showDependencies="false" x="0.0" y="0.0" />
        <SelectedNodes />
        <Categories />
      </Diagram>
      
      """;

    public static void showDiagramFromFile(Project project, PsiElement originalElement) {
      File file = createLocalFile(project, System.currentTimeMillis() + ".uml", new JourneyNodeIdentity(originalElement));
      VirtualFile virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file.toPath());

      if (virtualFile != null) {
        // For some reason file is not being opened right after the creation.
        new Alarm().addRequest(() -> {
          FileEditorManager.getInstance(project).openFileEditor(new OpenFileDescriptor(project, virtualFile), true);
        }, 500);
      }
      else {
        LOG.error("Failed to convert File to VirtualFile");
      }
    }

    public static File createLocalFile(Project project, String name, JourneyNodeIdentity originalElement) {
      try {
        File journeyDir = new File(project.getBasePath() + "/.journey");
        if (!journeyDir.exists()) {
          journeyDir.mkdirs();
        }
        File localFile = new File(journeyDir, name);
        String fqn = JourneyDiagramProvider.getInstance().getVfsResolver().getQualifiedName(originalElement);
        Objects.requireNonNull(fqn);
        FileUtil.writeToFile(localFile, INITIAL.replace(ORIGINAL_ELEMENT_PLACEHOLDER, fqn));
        return localFile;
      }
      catch (IOException e) {
        LOG.error(e);
        throw new RuntimeException(e);
      }
    }
  }
}
