package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.File;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderEditor extends UserDataHolderBase implements FileEditor, EditorCallback {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderEditor");

  private final static String SCENE_CARD = "scene_builder";
  private final static String ERROR_CARD = "error";

  private final Project myProject;
  private final VirtualFile myFile;
  private final SceneBuilderProvider myCreatorProvider;

  private final CardLayout myLayout = new CardLayout();
  private final JPanel myPanel = new JPanel(myLayout);

  //private final JPanel myErrorPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 10, 5, true, false));
  private final JPanel myErrorPanel = new JPanel(new BorderLayout());
  private final HyperlinkLabel myErrorLabel = new HyperlinkLabel();
  private JTextArea myErrorStack;

  private final Document myDocument;
  private final ExternalChangeListener myChangeListener;

  private SceneBuilderCreator myBuilderCreator;
  private SceneBuilder mySceneBuilder;

  public SceneBuilderEditor(@NotNull Project project, @NotNull VirtualFile file, SceneBuilderProvider creatorProvider) {
    myProject = project;
    myFile = file;
    myCreatorProvider = creatorProvider;

    myDocument = FileDocumentManager.getInstance().getDocument(file);
    myChangeListener = new ExternalChangeListener();

    createErrorPage();
  }

  private void createErrorPage() {
    myErrorLabel.setOpaque(false);

    myErrorLabel.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        initSceneBuilder(true);
      }
    });

    myErrorStack = new JTextArea(50, 20);
    myErrorStack.setEditable(false);

    myErrorPanel.add(myErrorLabel, BorderLayout.NORTH);
    myErrorPanel.add(ScrollPaneFactory.createScrollPane(myErrorStack), BorderLayout.CENTER);
    myPanel.add(myErrorPanel);
  }

  private void showErrorPage(State state, Throwable e) {
    if (e != null) {
      LOG.info(e);
    }

    removeSceneBuilder();

    if (e == null) {
      if (state == State.CREATE_ERROR) {
        myErrorLabel.setHyperlinkText("JavaFX Scene Builder initialize error", "", "");
        myErrorLabel.setIcon(Messages.getErrorIcon());
      }
      else {
        if (state == State.EMPTY_PATH) {
          myErrorLabel.setHyperlinkText("Please configure JavaFX Scene Builder ", "path", "");
        }
        else {
          myErrorLabel.setHyperlinkText("Please reconfigure JavaFX Scene Builder ", "path", "");
        }
        myErrorLabel.setIcon(Messages.getWarningIcon());
      }

      myErrorStack.setText(null);
      myErrorStack.setVisible(false);
    }
    else {
      String message = e.getMessage();
      if (message == null) {
        message = e.getClass().getName();
      }

      myErrorLabel.setHyperlinkText("Error: " + message, "", "");
      myErrorLabel.setIcon(Messages.getErrorIcon());

      myErrorStack.setText(ExceptionUtil.getThrowableText(e));
      myErrorStack.setVisible(true);
    }
    myLayout.show(myPanel, ERROR_CARD);
  }

  @Override
  public void saveChanges(final String content) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (mySceneBuilder != null) {

          if (!myDocument.isWritable() && ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(myFile).hasReadonlyFiles()) {
            return;
          }

          try {
            myChangeListener.setRunState(false);

            // XXX: strange behavior with undo/redo

            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
                  @Override
                  public void run() {
                    myDocument.setText(content);
                  }
                }, "JavaFX Scene Builder edit operation", null);
              }
            });
          }
          finally {
            myChangeListener.setRunState(true);
          }
        }
      }
    });
  }

  @Override
  public void handleError(final Throwable e) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        showErrorPage(null, e);
      }
    });
  }

  private void initSceneBuilder(boolean choosePathIfEmpty) {
    if (choosePathIfEmpty || myBuilderCreator == null) {
      myBuilderCreator = myCreatorProvider.get(myProject, choosePathIfEmpty);
      updateState();
    }
    else {
      SceneBuilderCreator creator = myCreatorProvider.get(null, false);
      if (myBuilderCreator.equals(creator)) {
        if (myBuilderCreator.getState() == State.OK) {
          myChangeListener.checkContent();
        }
      }
      else {
        updateState();
      }
    }
  }

  private void updateState() {
    if (myBuilderCreator.getState() == State.OK) {
      addSceneBuilder();
    }
    else {
      showErrorPage(myBuilderCreator.getState(), null);
    }
  }

  private void addSceneBuilder() {
    removeSceneBuilder();

    try {
      FileDocumentManager.getInstance().saveDocument(myDocument);

      mySceneBuilder = myBuilderCreator.create(new File(myFile.getPath()).toURI().toURL(), this);

      myPanel.add(mySceneBuilder.getPanel(), SCENE_CARD);
      myLayout.show(myPanel, SCENE_CARD);

      myChangeListener.start();
    }
    catch (Throwable e) {
      showErrorPage(null, e);
    }
  }

  private void removeSceneBuilder() {
    myChangeListener.stop();

    if (mySceneBuilder != null) {
      myPanel.remove(mySceneBuilder.getPanel());
      mySceneBuilder.close();
      mySceneBuilder = null;
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySceneBuilder == null ? myErrorPanel : mySceneBuilder.getPanel();
  }

  @Override
  public void dispose() {
    removeSceneBuilder();
    myChangeListener.dispose();
  }

  @NotNull
  @Override
  public String getName() {
    return "Scene Builder";
  }

  @Override
  public void selectNotify() {
    initSceneBuilder(false);
  }

  @Override
  public void deselectNotify() {
    myChangeListener.stop();
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public boolean isModified() {
    return true;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  private class ExternalChangeListener extends DocumentAdapter {
    private volatile boolean myRunState;
    private String myContent;

    public ExternalChangeListener() {
      myDocument.addDocumentListener(this);
    }

    public void start() {
      if (!myRunState) {
        myRunState = true;
        myContent = null;
      }
    }

    public void stop() {
      if (myRunState) {
        myRunState = false;
        myContent = myDocument.getText();
      }
    }

    public void setRunState(boolean state) {
      myRunState = state;
    }

    public void dispose() {
      myDocument.removeDocumentListener(this);
    }

    public void checkContent() {
      if (!myRunState && !myDocument.getText().equals(myContent)) {
        addSceneBuilder();
        start();
      }
    }

    @Override
    public void documentChanged(DocumentEvent e) {
      if (myRunState) {
        addSceneBuilder();
      }
    }
  }
}