package org.intellij.images.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import org.intellij.images.editor.ImageDocument;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.options.*;
import org.intellij.images.ui.ImageComponent;
import org.intellij.images.vfs.IfsUtil;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;

/**
 * Image viewer implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
class ImageEditorImpl extends VirtualFileAdapter implements ImageEditor {
    private static final Logger LOGGER = Logger.getInstance("ImageEditor");
    private final PropertyChangeListener optionsChangeListener = new OptionsChangeListener();
    private final Project project;
    private final ImageEditorUI editorUI;
    private final VirtualFile file;
    private boolean disposed = false;

    ImageEditorImpl(Project project, VirtualFile file) throws IOException {
        this.project = project;
        this.file = file;

        // Options
        Options options = OptionsManager.getInstance().getOptions();
        editorUI = new ImageEditorUI(IfsUtil.getImage(file), options.getEditorOptions());
        options.addPropertyChangeListener(optionsChangeListener);
    }

    public JComponent getComponent() {
        return editorUI.getRootPane();
    }

    public Project getProject() {
        return project;
    }

    public ImageDocument getDocument() {
        return editorUI.getImageComponent().getDocument();
    }

    public JComponent getContentComponent() {
        return editorUI.getImageComponent();
    }

    public void setTransparencyChessboardVisible(boolean visible) {
        editorUI.getImageComponent().setTransparencyChessboardVisible(visible);
        editorUI.repaint();
    }

    public boolean isTransparencyChessboardVisible() {
        return editorUI.getImageComponent().isTransparencyChessboardVisible();
    }

    public void setGridVisible(boolean visible) {
        editorUI.getImageComponent().setGridVisible(visible);
        editorUI.repaint();
    }

    public boolean isGridVisible() {
        return editorUI.getImageComponent().isGridVisible();
    }

    public boolean isDisposed() {
        return disposed;
    }

    public ImageZoomModel getZoomModel() {
        return editorUI.getZoomModel();
    }

    public void dispose() {
        Options options = OptionsManager.getInstance().getOptions();
        options.removePropertyChangeListener(optionsChangeListener);
        editorUI.dispose();
        disposed = true;
    }

    public void contentsChanged(VirtualFileEvent virtualFileEvent) {
        super.contentsChanged(virtualFileEvent);
        if (file.equals(virtualFileEvent.getFile())) {
            // Change document
            file.refresh(true, false, new Runnable() {
                public void run() {
                    try {
                        editorUI.getImageComponent().getDocument().setValue(IfsUtil.getImage(file));
                    } catch (IOException e) {
                        LOGGER.error(e);
                    }
                }
            });

        }
    }

    private class OptionsChangeListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            Options options = (Options)evt.getSource();
            EditorOptions editorOptions = options.getEditorOptions();
            TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
            GridOptions gridOptions = editorOptions.getGridOptions();

            ImageComponent imageComponent = editorUI.getImageComponent();
            imageComponent.setTransparencyChessboardCellSize(chessboardOptions.getCellSize());
            imageComponent.setTransparencyChessboardWhiteColor(chessboardOptions.getWhiteColor());
            imageComponent.setTransparencyChessboardBlankColor(chessboardOptions.getBlackColor());
            imageComponent.setGridLineZoomFactor(gridOptions.getLineZoomFactor());
            imageComponent.setGridLineSpan(gridOptions.getLineSpan());
            imageComponent.setGridLineColor(gridOptions.getLineColor());
        }
    }
}
