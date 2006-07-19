/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.options.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.DocumentAdapter;
import org.intellij.images.ImagesBundle;
import org.intellij.images.options.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Options UI form bean.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class OptionsUIForm {
    private JPanel contentPane;
    private JCheckBox showGrid;
    private JLabel gridLineZoomFactorlLabel;
    private JSpinner gridLineZoomFactor;
    private JLabel gridLineSpanLabel;
    private JSpinner gridLineSpan;
    private JCheckBox showChessboard;
    private JSpinner chessboardSize;
    private JLabel chessboardSizeLabel;
    private JCheckBox wheelZooming;
    private JCheckBox smartZooming;
    private JSpinner smartZoomingWidth;
    private JLabel smartZoomingWidthLabel;
    private JSpinner smartZoomingHeight;
    private JLabel smartZoomingHeightLabel;
    private JLabel gridLineColorLabel;
    private ColorPanel gridLineColor;
    private JLabel chessboardWhiteColorLabel;
    private JLabel chessboardBlackColorLabel;
    private ColorPanel chessboardBlackColor;
    private ColorPanel chessboardWhiteColor;
    private JLabel externalEditorLabel;
    private TextFieldWithBrowseButton externalEditorPath;

    // Options
    private final Options options = new OptionsImpl();

    OptionsUIForm() {
        // Setup labels
        gridLineZoomFactorlLabel.setLabelFor(gridLineZoomFactor);
        gridLineSpanLabel.setLabelFor(gridLineSpan);
        chessboardSizeLabel.setLabelFor(chessboardSize);
        smartZoomingWidthLabel.setLabelFor(smartZoomingWidth);
        smartZoomingHeightLabel.setLabelFor(smartZoomingHeight);
        gridLineColorLabel.setLabelFor(gridLineColor);
        chessboardWhiteColorLabel.setLabelFor(chessboardWhiteColor);
        chessboardBlackColorLabel.setLabelFor(chessboardBlackColor);
        externalEditorLabel.setLabelFor(externalEditorPath);

        // Setup listeners for enabling and disabling linked checkbox groups
        smartZooming.addItemListener(new LinkEnabledListener(new JComponent[]{
                        smartZoomingHeightLabel,
                        smartZoomingHeight,
                        smartZoomingWidthLabel,
                        smartZoomingWidth,
                    }));
        // Setup spinners models
        gridLineZoomFactor.setModel(new SpinnerNumberModel(GridOptions.DEFAULT_LINE_ZOOM_FACTOR, 2, 8, 1));
        gridLineSpan.setModel(new SpinnerNumberModel(GridOptions.DEFAULT_LINE_SPAN, 1, 100, 1));
        chessboardSize.setModel(new SpinnerNumberModel(TransparencyChessboardOptions.DEFAULT_CELL_SIZE, 1, 100, 1));
        smartZoomingWidth.setModel(new SpinnerNumberModel(ZoomOptions.DEFAULT_PREFFERED_SIZE.width, 1, 9999, 1));
        smartZoomingHeight.setModel(new SpinnerNumberModel(ZoomOptions.DEFAULT_PREFFERED_SIZE.height, 1, 9999, 1));

        // Setup listeners for chnages
        showGrid.addItemListener(new CheckboxOptionsListener(GridOptions.ATTR_SHOW_DEFAULT));
        gridLineZoomFactor.addChangeListener(new SpinnerOptionsListener(GridOptions.ATTR_LINE_ZOOM_FACTOR));
        gridLineSpan.addChangeListener(new SpinnerOptionsListener(GridOptions.ATTR_LINE_SPAN));
        showChessboard.addItemListener(new CheckboxOptionsListener(TransparencyChessboardOptions.ATTR_SHOW_DEFAULT));
        chessboardSize.addChangeListener(new SpinnerOptionsListener(TransparencyChessboardOptions.ATTR_CELL_SIZE));
        wheelZooming.addItemListener(new CheckboxOptionsListener(ZoomOptions.ATTR_WHEEL_ZOOMING));
        smartZooming.addItemListener(new CheckboxOptionsListener(ZoomOptions.ATTR_SMART_ZOOMING));
        smartZoomingWidth.addChangeListener(new SpinnerOptionsListener(ZoomOptions.ATTR_PREFFERED_WIDTH));
        smartZoomingHeight.addChangeListener(new SpinnerOptionsListener(ZoomOptions.ATTR_PREFFERED_HEIGHT));
        gridLineColor.addActionListener(new ColorOptionsListener(GridOptions.ATTR_LINE_COLOR));
        chessboardWhiteColor.addActionListener(new ColorOptionsListener(TransparencyChessboardOptions.ATTR_WHITE_COLOR));
        chessboardBlackColor.addActionListener(new ColorOptionsListener(TransparencyChessboardOptions.ATTR_BLACK_COLOR));
        externalEditorPath.getTextField().getDocument().addDocumentListener(new TextDocumentOptionsListener(ExternalEditorOptions.ATTR_EXECUTABLE_PATH));

        externalEditorPath.addActionListener(new ExternalEditorPathActionListener());

        updateUI();
    }

    public JPanel getContentPane() {
        return contentPane;
    }

    private static class LinkEnabledListener implements ItemListener {
        private final JComponent[] children;

        LinkEnabledListener(JComponent[] children) {
            this.children = children.clone();
        }

        public void itemStateChanged(ItemEvent e) {
            setSelected(e.getStateChange() == ItemEvent.SELECTED);
        }

        private void setSelected(boolean selected) {
            for (JComponent component : children) {
                component.setEnabled(selected);
            }
        }
    }

    public Options getOptions() {
        return options;
    }

    public void updateUI() {
        // Grid options
        EditorOptions editorOptions = options.getEditorOptions();
        ExternalEditorOptions externalEditorOptions = options.getExternalEditorOptions();

        GridOptions gridOptions = editorOptions.getGridOptions();
        showGrid.setSelected(gridOptions.isShowDefault());
        gridLineZoomFactor.setValue(gridOptions.getLineZoomFactor());
        gridLineSpan.setValue(gridOptions.getLineSpan());
        gridLineColor.setSelectedColor(gridOptions.getLineColor());
        TransparencyChessboardOptions transparencyChessboardOptions = editorOptions.getTransparencyChessboardOptions();
        showChessboard.setSelected(transparencyChessboardOptions.isShowDefault());
        chessboardSize.setValue(transparencyChessboardOptions.getCellSize());
        chessboardWhiteColor.setSelectedColor(transparencyChessboardOptions.getWhiteColor());
        chessboardBlackColor.setSelectedColor(transparencyChessboardOptions.getBlackColor());
        ZoomOptions zoomOptions = editorOptions.getZoomOptions();
        wheelZooming.setSelected(zoomOptions.isWheelZooming());
        smartZooming.setSelected(zoomOptions.isSmartZooming());
        Dimension prefferedSize = zoomOptions.getPrefferedSize();
        smartZoomingWidth.setValue(prefferedSize.width);
        smartZoomingHeight.setValue(prefferedSize.height);
        externalEditorPath.setText(externalEditorOptions.getExecutablePath());
    }

    private final class CheckboxOptionsListener implements ItemListener {
        private String name;

        private CheckboxOptionsListener(String name) {
            this.name = name;
        }

        @SuppressWarnings({"UnnecessaryBoxing"})
        public void itemStateChanged(ItemEvent e) {
            options.setOption(name, Boolean.valueOf(ItemEvent.SELECTED == e.getStateChange()));
        }
    }

    private final class SpinnerOptionsListener implements ChangeListener {
        private String name;

        private SpinnerOptionsListener(String name) {
            this.name = name;
        }

        public void stateChanged(ChangeEvent e) {
            JSpinner source = (JSpinner)e.getSource();
            options.setOption(name, source.getValue());
        }
    }

    private final class ColorOptionsListener implements ActionListener {
        private String name;

        private ColorOptionsListener(String name) {
            this.name = name;
        }

        public void actionPerformed(ActionEvent e) {
            ColorPanel source = (ColorPanel)e.getSource();
            options.setOption(name, source.getSelectedColor());
        }
    }

    private final class TextDocumentOptionsListener extends DocumentAdapter {
        private final String name;

        public TextDocumentOptionsListener(String name) {
            this.name = name;
        }

        protected void textChanged(DocumentEvent documentEvent) {
            Document document = documentEvent.getDocument();
            Position startPosition = document.getStartPosition();
            try {
                options.setOption(name, document.getText(startPosition.getOffset(), document.getLength()));
            } catch (BadLocationException e) {
                // Ignore
            }
        }
    }

    private final class ExternalEditorPathActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            Application application = ApplicationManager.getApplication();
            application.runWriteAction(new Runnable() {
                public void run() {
                    VirtualFile previous = LocalFileSystem.getInstance().refreshAndFindFileByPath(
                        externalEditorPath.getText().replace('\\', '/')
                    );

                    FileChooserDescriptor fileDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
                    fileDescriptor.setShowFileSystemRoots(true);
                    fileDescriptor.setTitle(ImagesBundle.message("select.external.executable.title"));
                    fileDescriptor.setDescription(ImagesBundle.message("select.external.executable.message"));
                    VirtualFile[] virtualFiles = FileChooser.chooseFiles(externalEditorPath, fileDescriptor, previous);

                    if (virtualFiles != null && virtualFiles.length > 0) {
                        String path = virtualFiles[0].getPath();
                        externalEditorPath.setText(path);
                    }
                }
            });
        }
    }
}
