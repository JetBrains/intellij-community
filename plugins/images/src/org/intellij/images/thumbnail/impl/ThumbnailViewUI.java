/** $Id$ */
package org.intellij.images.thumbnail.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.*;
import com.intellij.pom.Navigatable;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.options.*;
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
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

final class ThumbnailViewUI extends JPanel implements DataProvider, Disposable {
    private static final String THUMBNAILS_POPUP_GROUP = "Images.ThumbnailsPopupMenu";
    private static final String THUMBNAILS_TOOLBAR_GROUP = "Images.ThumbnailsToolbar";

    private final VFSListener vfsListener = new VFSListener();
    private final ThumbnailViewImpl thumbnailView;

    private ThumbnailListCellRenderer cellRenderer;
    private JList list;
    private final OptionsChangeListener optionsListener = new OptionsChangeListener();

    public ThumbnailViewUI(ThumbnailViewImpl thumbnailView) {
        super(new BorderLayout());

        this.thumbnailView = thumbnailView;
    }

    public void createUI() {
        if (cellRenderer == null || list == null) {
            cellRenderer = new ThumbnailListCellRenderer();
            ImageComponent imageComponent = cellRenderer.getImageComponent();
            imageComponent.setTransparencyChessboardVisible(true);

            VirtualFileManager.getInstance().addVirtualFileListener(vfsListener);

            Options options = OptionsManager.getInstance().getOptions();
            EditorOptions editorOptions = options.getEditorOptions();
            // Set options
            TransparencyChessboardOptions chessboardOptions = editorOptions.getTransparencyChessboardOptions();
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

            ThumbnailComponentUI ui = (ThumbnailComponentUI)UIManager.getUI(cellRenderer);
            Dimension preferredSize = ui.getPreferredSize(cellRenderer);

            list.setFixedCellWidth(preferredSize.width);
            list.setFixedCellHeight(preferredSize.height);


            JScrollPane scrollPane = new JScrollPane(
                list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            );

            JPanel toolBar = new JPanel();
            toolBar.setLayout(new BorderLayout());

            add(toolBar, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);

            ActionManager actionManager = ActionManager.getInstance();
            ActionGroup actionGroup = (ActionGroup)actionManager.getAction(THUMBNAILS_TOOLBAR_GROUP);
            ActionToolbar actionToolbar = actionManager.createActionToolbar(THUMBNAILS_TOOLBAR_GROUP, actionGroup, true);

            toolBar.add(actionToolbar.getComponent(), BorderLayout.WEST);

            refresh();
        }
    }

    public void refresh() {
        if (list != null && cellRenderer != null) {
            DefaultListModel model = (DefaultListModel)list.getModel();
            model.clear();

            Set<VirtualFile> files = findFiles(thumbnailView.getRoot().getChildren());
            VirtualFile[] virtualFiles = files.toArray(new VirtualFile[]{});
            Arrays.sort(
                virtualFiles, new Comparator<VirtualFile>() {
                public int compare(VirtualFile o1, VirtualFile o2) {
                    if (o1.isDirectory() && !o2.isDirectory()) {
                        return -1;
                    }
                    if (o2.isDirectory() && !o1.isDirectory()) {
                        return 1;
                    }

                    return o1.getPath().compareTo(o2.getPath());
                }
            }
            );

            model.ensureCapacity(model.size() + virtualFiles.length);
            for (VirtualFile virtualFile : virtualFiles) {
                model.addElement(virtualFile);
            }
        }
    }

    private static final class ThumbnailListCellRenderer extends ThumbnailComponent
        implements ListCellRenderer {
        public Component getListCellRendererComponent(
            JList list, Object value, int index, boolean isSelected, boolean cellHasFocus
        ) {
            if (value instanceof VirtualFile) {
                VirtualFile file = (VirtualFile)value;
                setFileName(file.getName());
                setToolTipText(file.getPath());
                setDirectory(file.isDirectory());
                if (!file.isDirectory()) {
                    // File rendering
                    setFileSize(file.getLength());
                    try {
                        BufferedImage image = IfsUtil.getImage(file);
                        ImageComponent imageComponent = getImageComponent();
                        imageComponent.getDocument().setValue(image);
                        setFormat(IfsUtil.getFormat(file));
                    } catch (IOException e) {
                        // Ignore
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
            } else {
                files.add(file);
            }
        } else if (typeManager.isImage(file)) {
            files.add(file);
        }
        return files;
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
                ActionGroup actionGroup = (ActionGroup)actionManager.getAction(THUMBNAILS_POPUP_GROUP);
                ActionPopupMenu menu = actionManager.createActionPopupMenu(THUMBNAILS_POPUP_GROUP, actionGroup);
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
            return new ThumbnailNavigatable(getSelectedFiles());
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
        private VirtualFile[] files;

        public ThumbnailNavigatable(VirtualFile[] files) {
            this.files = files;
        }

        public void navigate(boolean requestFocus) {
            if (files != null) {
                FileEditorManager manager = FileEditorManager.getInstance(thumbnailView.getProject());
                if (files.length > 2) {
                    for (VirtualFile file : files) {
                        manager.openFile(file, false);
                    }
                } else {
                    manager.openFile(files[0], true);
                }
            }
        }

        public boolean canNavigate() {
            return files != null;
        }

        public boolean canNavigateToSource() {
            return files != null;
        }
    }

    private final class VFSListener extends VirtualFileAdapter {
        public void contentsChanged(VirtualFileEvent event) {
            VirtualFile file = event.getFile();
            if (list != null) {
                int index = ((DefaultListModel)list.getModel()).indexOf(file);
                if (index != -1) {
                    Rectangle cellBounds = list.getCellBounds(index, index);
                    repaint(cellBounds);
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
}
