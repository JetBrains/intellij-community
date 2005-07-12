/** $Id$ */
package org.intellij.images.thumbnail.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.Navigatable;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.options.*;
import org.intellij.images.thumbnail.actionSystem.ThumbnailsActions;
import org.intellij.images.ui.ImageComponent;
import org.intellij.images.ui.ThumbnailComponent;
import org.intellij.images.ui.ThumbnailComponentUI;
import org.intellij.images.vfs.IfsUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

final class ThumbnailViewUI extends JPanel implements DataProvider, Disposable {
    private static final VirtualFile[] EMPTY_VIRTUAL_FILE_ARRAY = new VirtualFile[] {};

    private final VFSListener vfsListener = new VFSListener();
    private final OptionsChangeListener optionsListener = new OptionsChangeListener();

    private final ThumbnailViewImpl thumbnailView;
    private ThumbnailListCellRenderer cellRenderer;
    private JList list;

    public ThumbnailViewUI(ThumbnailViewImpl thumbnailView) {
        super(new BorderLayout());

        this.thumbnailView = thumbnailView;
    }

    private void createUI() {
        if (cellRenderer == null || list == null) {
            cellRenderer = new ThumbnailListCellRenderer();
            ImageComponent imageComponent = cellRenderer.getImageComponent();

            VirtualFileManager.getInstance().addVirtualFileListener(vfsListener);

            Options options = OptionsManager.getInstance().getOptions();
            EditorOptions editorOptions = options.getEditorOptions();
            // Set options
            TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
            imageComponent.setTransparencyChessboardVisible(chessboardOptions.isShowDefault());
            imageComponent.setTransparencyChessboardCellSize(chessboardOptions.getCellSize());
            imageComponent.setTransparencyChessboardWhiteColor(chessboardOptions.getWhiteColor());
            imageComponent.setTransparencyChessboardBlankColor(chessboardOptions.getBlackColor());

            options.addPropertyChangeListener(optionsListener);

            list = new JList();
            list.setModel(new DefaultListModel());
            list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
            list.setVisibleRowCount(-1);
            list.setCellRenderer(cellRenderer);

            list.addMouseListener(new ThumbnailsMouseAdapter());

            ThumbnailComponentUI componentUI = (ThumbnailComponentUI)UIManager.getUI(cellRenderer);
            Dimension preferredSize = componentUI.getPreferredSize(cellRenderer);

            list.setFixedCellWidth(preferredSize.width);
            list.setFixedCellHeight(preferredSize.height);


            JScrollPane scrollPane = new JScrollPane(
                list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            );

            ActionManager actionManager = ActionManager.getInstance();
            ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ThumbnailsActions.GROUP_TOOLBAR);
            ActionToolbar actionToolbar = actionManager.createActionToolbar(
                ThumbnailsActions.GROUP_TOOLBAR, actionGroup, true
            );

            JComponent toolbar = actionToolbar.getComponent();

            FocusRequester focusRequester = new FocusRequester();
            toolbar.addMouseListener(focusRequester);
            scrollPane.addMouseListener(focusRequester);

            add(toolbar, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);
        }
    }

    public void refresh() {
        createUI();

        DefaultListModel model = (DefaultListModel)list.getModel();
        model.clear();

        Set<VirtualFile> files = findFiles(thumbnailView.getRoot().getChildren());
        VirtualFile[] virtualFiles = files.toArray(EMPTY_VIRTUAL_FILE_ARRAY);
        Arrays.sort(
            virtualFiles, new Comparator<VirtualFile>() {
            public int compare(VirtualFile o1, VirtualFile o2) {
                if (o1.isDirectory() && !o2.isDirectory()) {
                    return -1;
                }
                if (o2.isDirectory() && !o1.isDirectory()) {
                    return 1;
                }

                return o1.getPath().toLowerCase().compareTo(o2.getPath().toLowerCase());
            }
        }
        );

        model.ensureCapacity(model.size() + virtualFiles.length);
        for (VirtualFile virtualFile : virtualFiles) {
            model.addElement(virtualFile);
        }
    }

    public boolean isTransparencyChessboardVisible() {
        createUI();
        return cellRenderer.getImageComponent().isTransparencyChessboardVisible();
    }

    public void setTransparencyChessboardVisible(boolean visible) {
        createUI();
        cellRenderer.getImageComponent().setTransparencyChessboardVisible(visible);
        list.repaint();
    }

    public void setSelected(VirtualFile file) {
        createUI();
        list.setSelectedValue(file, false);
    }

    public void scrollTo(VirtualFile file) {
        int index = ((DefaultListModel)list.getModel()).indexOf(file);
        if (index != -1) {
            list.scrollRectToVisible(list.getCellBounds(index, index));
        }
    }

    private static final class ThumbnailListCellRenderer extends ThumbnailComponent
        implements ListCellRenderer {
        private final ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();

        public Component getListCellRendererComponent(
            JList list, Object value, int index, boolean isSelected, boolean cellHasFocus
        ) {
            if (value instanceof VirtualFile) {
                VirtualFile file = (VirtualFile)value;
                setFileName(file.getName());
                setToolTipText(file.getPath());
                setDirectory(file.isDirectory());
                if (file.isDirectory()) {
                    int imagesCount = 0;
                    VirtualFile[] children = file.getChildren();
                    for (VirtualFile child : children) {
                        if (typeManager.isImage(child)) {
                            imagesCount++;
                            if (imagesCount > 100) {
                                break;
                            }
                        }
                    }
                    setImagesCount(imagesCount);
                } else {
                    // File rendering
                    setFileSize(file.getLength());
                    try {
                        BufferedImage image = IfsUtil.getImage(file);
                        ImageComponent imageComponent = getImageComponent();
                        imageComponent.getDocument().setValue(image);
                        setFormat(IfsUtil.getFormat(file));
                    } catch (Exception e) {
                        // Ignore
                        ImageComponent imageComponent = getImageComponent();
                        imageComponent.getDocument().setValue(null);
                    }
                }

            } else {
                ImageComponent imageComponent = getImageComponent();
                imageComponent.getDocument().setValue(null);
                setFileName(null);
                setFileSize(0);
                setToolTipText(null);
            }

            if (isSelected) {
                setForeground(list.getSelectionForeground());
                setBackground(list.getSelectionBackground());
            } else {
                setForeground(list.getForeground());
                setBackground(list.getBackground());
            }

            return this;
        }

    }

    private Set<VirtualFile> findFiles(VirtualFile[] roots) {
        Set<VirtualFile> files = new HashSet<VirtualFile>();
        for (VirtualFile root : roots) {
            files.addAll(findFiles(root));
        }
        return files;
    }

    private Set<VirtualFile> findFiles(VirtualFile file) {
        ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
        Set<VirtualFile> files = new HashSet<VirtualFile>(1);
        if (file.isDirectory()) {
            if (thumbnailView.isRecursive()) {
                files.addAll(findFiles(file.getChildren()));
            } else if (isImagesInDirectory(file)) {
                files.add(file);
            }
        } else if (typeManager.isImage(file)) {
            files.add(file);
        }
        return files;
    }

    private boolean isImagesInDirectory(VirtualFile dir) {
        ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
        VirtualFile[] files = dir.getChildren();
        for (VirtualFile file : files) {
            if (typeManager.isImage(file)) {
                return true;
            }
        }
        return false;
    }

    private final class ThumbnailsMouseAdapter extends MouseAdapter {
        public void mousePressed(MouseEvent e) {
            super.mousePressed(e);
            Point point = e.getPoint();
            int index = list.locationToIndex(point);
            if (index != -1 && list.getSelectedIndex() != index) {
                list.setSelectedIndex(index);
            }
        }

        public void mouseClicked(MouseEvent e) {
            if (MouseEvent.BUTTON1 == e.getButton() && e.getClickCount() == 2) {
                // Double click
                VirtualFile selected = (VirtualFile)list.getSelectedValue();
                if (selected.isDirectory()) {
                    thumbnailView.setRoot(selected);
                } else {
                    FileEditorManager fileEditorManager = FileEditorManager.getInstance(thumbnailView.getProject());
                    fileEditorManager.openFile(selected, true);
                }
                e.consume();
            }
            if (MouseEvent.BUTTON3 == e.getButton() && e.getClickCount() == 1) {
                // Single right click
                ActionManager actionManager = ActionManager.getInstance();
                ActionGroup actionGroup = (ActionGroup)actionManager.getAction(ThumbnailsActions.GROUP_POPUP);
                ActionPopupMenu menu = actionManager.createActionPopupMenu(ThumbnailsActions.ACTION_PLACE, actionGroup);
                JPopupMenu popupMenu = menu.getComponent();
                popupMenu.pack();
                popupMenu.show(e.getComponent(), e.getX(), e.getY());

                e.consume();
            }
        }

    }

    @Nullable public Object getData(String dataId) {
        if (DataConstants.PROJECT.equals(dataId)) {
            return thumbnailView.getProject();
        } else if (DataConstants.VIRTUAL_FILE.equals(dataId)) {
            VirtualFile[] selectedFiles = getSelectedFiles();
            return selectedFiles != null && selectedFiles.length > 0 ? selectedFiles[0] : null;
        } else if (DataConstants.VIRTUAL_FILE_ARRAY.equals(dataId)) {
            return getSelectedFiles();
        } else if (DataConstants.NAVIGATABLE.equals(dataId)) {
            VirtualFile[] selectedFiles = getSelectedFiles();
            return new ThumbnailNavigatable(selectedFiles != null && selectedFiles.length > 0 ? selectedFiles[0] : null);
        } else if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)) {
            VirtualFile[] selectedFiles = getSelectedFiles();
            if (selectedFiles != null) {
                Navigatable[] navigatables = new Navigatable[selectedFiles.length];
                for (int i = 0; i < selectedFiles.length; i++) {
                    VirtualFile selectedFile = selectedFiles[i];
                    navigatables[i] = new ThumbnailNavigatable(selectedFile);
                }
                return navigatables;
            }
            return null;
        }

        return null;
    }

    private VirtualFile[] getSelectedFiles() {
        if (list != null) {
            Object[] selectedValues = list.getSelectedValues();
            if (selectedValues != null) {
                VirtualFile[] files = new VirtualFile[selectedValues.length];
                for (int i = 0; i < selectedValues.length; i++) {
                    Object value = selectedValues[i];
                    files[i] = (VirtualFile)value;
                }
                return files;
            }
        }
        return null;
    }

    public void dispose() {
        removeAll();

        Options options = OptionsManager.getInstance().getOptions();
        options.removePropertyChangeListener(optionsListener);

        VirtualFileManager.getInstance().removeVirtualFileListener(vfsListener);

        list = null;
        cellRenderer = null;
    }

    private final class ThumbnailNavigatable implements Navigatable {
        private VirtualFile file;

        public ThumbnailNavigatable(VirtualFile file) {
            this.file = file;
        }

        public void navigate(boolean requestFocus) {
            if (file != null) {
                FileEditorManager manager = FileEditorManager.getInstance(thumbnailView.getProject());
                manager.openFile(file, true);
            }
        }

        public boolean canNavigate() {
            return file != null;
        }

        public boolean canNavigateToSource() {
            return file != null;
        }
    }

    private final class VFSListener extends VirtualFileAdapter {
        public void contentsChanged(VirtualFileEvent event) {
            VirtualFile file = event.getFile();
            if (list != null) {
                int index = ((DefaultListModel)list.getModel()).indexOf(file);
                if (index != -1) {
                    Rectangle cellBounds = list.getCellBounds(index, index);
                    list.repaint(cellBounds);
                }
            }
        }

        public void fileDeleted(VirtualFileEvent event) {
            VirtualFile file = event.getFile();
            if (list != null) {
                ((DefaultListModel)list.getModel()).removeElement(file);
            }
        }
    }

    private final class OptionsChangeListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            Options options = (Options)evt.getSource();
            EditorOptions editorOptions = options.getEditorOptions();
            TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
            GridOptions gridOptions = editorOptions.getGridOptions();

            ImageComponent imageComponent = cellRenderer.getImageComponent();
            imageComponent.setTransparencyChessboardCellSize(chessboardOptions.getCellSize());
            imageComponent.setTransparencyChessboardWhiteColor(chessboardOptions.getWhiteColor());
            imageComponent.setTransparencyChessboardBlankColor(chessboardOptions.getBlackColor());
            imageComponent.setGridLineZoomFactor(gridOptions.getLineZoomFactor());
            imageComponent.setGridLineSpan(gridOptions.getLineSpan());
            imageComponent.setGridLineColor(gridOptions.getLineColor());
        }
    }

    private class FocusRequester extends MouseAdapter {
        public void mouseClicked(MouseEvent e) {
            requestFocus();
        }
    }
}
