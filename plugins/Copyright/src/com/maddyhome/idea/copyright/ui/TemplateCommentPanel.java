package com.maddyhome.idea.copyright.ui;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.SupportCode;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.options.TemplateOptions;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import com.maddyhome.idea.copyright.util.VelocityHelper;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class TemplateCommentPanel
{
    public TemplateCommentPanel(FileType fileType, TemplateCommentPanel parentPanel, String[] locations)
    {
        this.parentPanel = parentPanel;
        overridePanel.setLayout(new BorderLayout());
        if (fileType != null)
        {
            ftOptionsPanel = new FileTypeOverridePanel();
            overridePanel.add(ftOptionsPanel.getMainComponent(), BorderLayout.CENTER);
        }
        else
        {
            overridePanel.setVisible(false);
        }

        this.fileType = fileType != null ? fileType : StdFileTypes.JAVA;
        tempOptionsPanel.setFileType(this.fileType);
        FileType alternate = FileTypeUtil.getInstance().getAlternate(this.fileType);
        if (alternate != null)
        {
            cbUseAlternate.setText("Use " + alternate.getName() + " Comments");
            cbUseAlternate.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    if (cbPreview.isSelected())
                    {
                        updateOverride();
                    }
                }
            });
        }
        else
        {
            cbUseAlternate.setVisible(false);
        }

        if (parentPanel != null)
        {
            parentPanel.tempOptionsPanel.addOptionChangeListener(new TemplateOptionsPanelListener()
            {
                public void optionChanged()
                {
                    updateOverride();
                }
            });
        }

        ButtonGroup group = new ButtonGroup();
        group.add(rbBefore);
        group.add(rbAfter);

        if (locations == null)
        {
            fileLocationPanel.setBorder(BorderFactory.createEmptyBorder());
        }
        else
        {
            fileLocations = new JRadioButton[locations.length];
            group = new ButtonGroup();
            for (int i = 0; i < fileLocations.length; i++)
            {
                fileLocations[i] = new JRadioButton(locations[i]);
                group.add(fileLocations[i]);

                fileLocationPanel.add(fileLocations[i], new GridConstraints(i, 0, 1, 1, GridConstraints.ANCHOR_WEST,
                    GridConstraints.FILL_NONE,
                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                    GridConstraints.SIZEPOLICY_FIXED, null, null, null));
            }
        }

        cbPreview.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent actionEvent)
            {
                if (cbPreview.isSelected())
                {
                    noticeText = noticeBody.getText();
                    showPreview(noticeText, tempOptionsPanel.getOptions());
                }
                else
                {
                    updateNoticeText(noticeText);
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        public void run()
                        {
                            noticeBody.requestFocus();
                        }
                    });
                }

                noticeBody.setEnabled(!cbPreview.isSelected());
            }
        });

        if (ftOptionsPanel != null)
        {
            ftOptionsPanel.addOptionChangeListener(new FileTypeOverridePanelListener()
            {
                public void optionChanged()
                {
                    updateOverride();
                }
            });
        }

        tempOptionsPanel.addOptionChangeListener(new TemplateOptionsPanelListener()
        {
            public void optionChanged()
            {
                if (cbPreview.isSelected())
                {
                    String text = noticeText;
                    if (ftOptionsPanel != null && ftOptionsPanel.getOptions() == LanguageOptions.USE_TEXT)
                    {
                        text = TemplateCommentPanel.this.parentPanel.getOptions().getNotice();
                    }
                    showPreview(text, tempOptionsPanel.getOptions());
                }
            }
        });

        noticeBody.addCaretListener(new CaretListener()
        {
            public void caretUpdate(CaretEvent caretEvent)
            {
                btnSelect.setEnabled(caretEvent.getDot() != caretEvent.getMark());
            }
        });
        noticeBody.getDocument().addDocumentListener(new DocumentListener()
        {
            public void changedUpdate(DocumentEvent documentEvent)
            {
                check();
            }

            public void insertUpdate(DocumentEvent documentEvent)
            {
                check();
            }

            public void removeUpdate(DocumentEvent documentEvent)
            {
                check();
            }

            private void check()
            {
                btnValidate.setEnabled(noticeBody.getText().length() > 0 && noticeBody.isEnabled());
            }
        });

        btnSelect.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent actionEvent)
            {
                txtKeyword.setText(noticeBody.getSelectedText());
            }
        });

        btnValidate.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent actionEvent)
            {
                try
                {
                    VelocityHelper.verify(noticeBody.getText());
                    JOptionPane.showMessageDialog(null, "Velocity template valid.", "Validation",
                        JOptionPane.INFORMATION_MESSAGE);
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(null, "Velocity template error:\n" + e.getMessage(), "Validation",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        lblKeyword.setLabelFor(txtKeyword);

        noticeBody.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));

        noticeBody.setColumns(CodeStyleSettingsManager.getInstance().getCurrentSettings().RIGHT_MARGIN);
    }

    public JComponent getMainComponent()
    {
        return mainPanel;
    }

    public boolean isModified(LanguageOptions options)
    {
        return !getOptions().equals(options);
    }

    public void setOptions(LanguageOptions options)
    {
        tempOptionsPanel.setOptions(options.getTemplateOptions());
        noticeText = options.getNotice();
        cbPreview.setSelected(false);
        updateNoticeText(options.getNotice());
        noticeScroll.getVerticalScrollBar().setValue(0);
        txtKeyword.setText(options.getKeyword());
        txtKeyword.setCaretPosition(0);
        if (ftOptionsPanel != null)
        {
            ftOptionsPanel.setOptions(options.getFileTypeOverride());
        }
        if (options.isRelativeBefore())
        {
            rbBefore.setSelected(true);
        }
        else
        {
            rbAfter.setSelected(true);
        }
        cbAddBlank.setSelected(options.isAddBlankAfter());
        cbUseAlternate.setSelected(options.isUseAlternate());

        if (fileLocations != null)
        {
            int choice = options.getFileLocation() - 1;
            choice = Math.max(0, Math.min(choice, fileLocations.length - 1));
            fileLocations[choice].setSelected(true);
        }

        updateOverride();
    }

    public LanguageOptions getOptions()
    {
        // If this is a fully custom comment we should really ensure there are no blank lines in the comments outside
        // of a block comment. If there are any blank lines the replacement logic will fall apart.
        LanguageOptions res = new LanguageOptions();
        res.setTemplateOptions(tempOptionsPanel.getOptions());
        if (cbPreview.isSelected())
        {
            res.setNotice(noticeText);
        }
        else
        {
            res.setNotice(noticeBody.getText());
        }

        if (ftOptionsPanel != null)
        {
            res.setFileTypeOverride(ftOptionsPanel.getOptions());
        }
        else
        {
            res.setFileTypeOverride(LanguageOptions.USE_CUSTOM);
        }
        res.setKeyword(txtKeyword.getText());
        res.setRelativeBefore(rbBefore.isSelected());
        res.setAddBlankAfter(cbAddBlank.isSelected());
        res.setUseAlternate(cbUseAlternate.isSelected());
        if (fileLocations != null)
        {
            for (int i = 0; i < fileLocations.length; i++)
            {
                if (fileLocations[i].isSelected())
                {
                    res.setFileLocation(i + 1);
                }
            }
        }

        return res;
    }

    private int getOverrideChoice()
    {
        int choice = LanguageOptions.USE_CUSTOM;
        if (ftOptionsPanel != null)
        {
            choice = ftOptionsPanel.getOptions();
        }

        return choice;
    }

    private void updateOverride()
    {
        int choice = getOverrideChoice();
        LanguageOptions parentOpts = parentPanel != null ? parentPanel.getOptions() : null;
        switch (choice)
        {
            case LanguageOptions.USE_NONE:
                tempOptionsPanel.setEnabled(false);
                cbPreview.setSelected(true);
                noticeBody.setText("");
                cbPreview.setEnabled(false);
                noticeBody.setEnabled(false);
                lblKeyword.setEnabled(false);
                txtKeyword.setEnabled(false);
                btnSelect.setEnabled(false);
                btnValidate.setEnabled(false);
                rbBefore.setEnabled(false);
                rbAfter.setEnabled(false);
                cbAddBlank.setEnabled(false);
                if (fileLocations != null)
                {
                    for (JRadioButton fileLocation : fileLocations)
                    {
                        fileLocation.setEnabled(false);
                    }
                }
                break;
            case LanguageOptions.USE_TEMPLATE:
                tempOptionsPanel.setEnabled(false);
                cbPreview.setSelected(true);
                if (parentOpts != null)
                {
                    showPreview(parentOpts.getNotice(), parentOpts.getTemplateOptions());
                }
                cbPreview.setEnabled(false);
                noticeBody.setEnabled(false);
                lblKeyword.setEnabled(false);
                txtKeyword.setEnabled(false);
                btnSelect.setEnabled(false);
                btnValidate.setEnabled(false);
                rbBefore.setEnabled(false);
                rbAfter.setEnabled(false);
                cbAddBlank.setEnabled(false);
                if (fileLocations != null)
                {
                    for (JRadioButton fileLocation : fileLocations)
                    {
                        fileLocation.setEnabled(true);
                    }
                }
                break;
            case LanguageOptions.USE_TEXT:
                tempOptionsPanel.setEnabled(true);
                cbPreview.setSelected(true);
                if (parentOpts != null)
                {
                    showPreview(parentOpts.getNotice(), tempOptionsPanel.getOptions());
                }
                cbPreview.setEnabled(false);
                noticeBody.setEnabled(false);
                lblKeyword.setEnabled(false);
                txtKeyword.setEnabled(false);
                btnSelect.setEnabled(false);
                btnValidate.setEnabled(false);
                rbBefore.setEnabled(true);
                rbAfter.setEnabled(true);
                cbAddBlank.setEnabled(true);
                if (fileLocations != null)
                {
                    for (JRadioButton fileLocation : fileLocations)
                    {
                        fileLocation.setEnabled(true);
                    }
                }
                break;
            case LanguageOptions.USE_CUSTOM:
                if (parentOpts == null)
                {
                    tempOptionsPanel.setEnabled(true);
                    cbPreview.setSelected(false);
                    cbPreview.setEnabled(true);
                }
                else
                {
                    tempOptionsPanel.setEnabled(false);
                    cbPreview.setSelected(false);
                    cbPreview.setEnabled(false);
                }
                noticeBody.setEnabled(true);
                updateNoticeText(noticeText);
                lblKeyword.setEnabled(true);
                txtKeyword.setEnabled(true);
                btnSelect.setEnabled(false);
                btnValidate.setEnabled(noticeBody.getText().length() > 0);
                rbBefore.setEnabled(true);
                rbAfter.setEnabled(true);
                cbAddBlank.setEnabled(true);
                if (fileLocations != null)
                {
                    for (JRadioButton fileLocation : fileLocations)
                    {
                        fileLocation.setEnabled(true);
                    }
                }
                break;
        }
    }

    private void showPreview(String text, TemplateOptions options)
    {
        String res = FileTypeUtil.buildComment(fileType, cbUseAlternate.isSelected(), text, options);
        updateNoticeText(res);
    }

    private void updateNoticeText(String text)
    {
        noticeBody.setText(text);
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                noticeBody.setCaretPosition(0);
                noticeScroll.getHorizontalScrollBar().setValue(0);
                noticeScroll.getVerticalScrollBar().setValue(0);
            }
        });
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR call it in your
     * code!
     */
    private void $$$setupUI$$$()
    {
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(3, 1, new Insets(5, 5, 5, 5), -1, -1));
        panel1.add(mainPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null));
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
            "Copyright Notice (Velocity Template)"));
        noticeScroll = new JScrollPane();
        panel2.add(noticeScroll, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null));
        noticeBody = new JTextArea();
        noticeScroll.setViewportView(noticeBody);
        cbPreview = new JCheckBox();
        cbPreview.setText("Preview");
        cbPreview.setMnemonic(87);
        SupportCode.setDisplayedMnemonicIndex(cbPreview, 6);
        panel2.add(cbPreview, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        btnValidate = new JButton();
        btnValidate.setText("Validate");
        btnValidate.setMnemonic(86);
        SupportCode.setDisplayedMnemonicIndex(btnValidate, 0);
        panel2.add(btnValidate, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST,
            GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null));
        lblKeyword = new JLabel();
        lblKeyword.setText("Copyright Keyword:");
        lblKeyword.setDisplayedMnemonic(75);
        SupportCode.setDisplayedMnemonicIndex(lblKeyword, 10);
        panel3.add(lblKeyword, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        txtKeyword = new JTextField();
        txtKeyword.setColumns(20);
        panel3.add(txtKeyword, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null));
        btnSelect = new JButton();
        btnSelect.setText("Select");
        btnSelect.setMnemonic(83);
        SupportCode.setDisplayedMnemonicIndex(btnSelect, 0);
        panel3.add(btnSelect, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 4, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST,
            GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null));
        overridePanel = new JPanel();
        panel4.add(overridePanel, new GridConstraints(0, 0, 2, 1, GridConstraints.ANCHOR_NORTHWEST,
            GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(0, 0), null,
            null));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), 0, 0));
        panel4.add(panel5, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        panel5.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Relative Location"));
        rbBefore = new JRadioButton();
        rbBefore.setText("Before Other Comments");
        rbBefore.setMnemonic(79);
        SupportCode.setDisplayedMnemonicIndex(rbBefore, 3);
        panel5.add(rbBefore, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        rbAfter = new JRadioButton();
        rbAfter.setText("After Other Comments");
        rbAfter.setMnemonic(82);
        SupportCode.setDisplayedMnemonicIndex(rbAfter, 4);
        panel5.add(rbAfter, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        cbAddBlank = new JCheckBox();
        cbAddBlank.setText("Add Blank Line After");
        cbAddBlank.setMnemonic(68);
        SupportCode.setDisplayedMnemonicIndex(cbAddBlank, 1);
        panel5.add(cbAddBlank, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null));
        tempOptionsPanel = new TemplateOptionsPanel();
        panel4.add(tempOptionsPanel, new GridConstraints(0, 1, 2, 1, GridConstraints.ANCHOR_NORTHWEST,
            GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
            null));
        fileLocationPanel = new JPanel();
        fileLocationPanel.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), 0, 0));
        panel4.add(fileLocationPanel, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_NORTHWEST,
            GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null));
        fileLocationPanel
            .setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "File Location"));
        cbUseAlternate = new JCheckBox();
        cbUseAlternate.setText("Use Alternate Comments");
        panel4.add(cbUseAlternate, new GridConstraints(1, 2, 1, 2, GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null));
    }

    private String noticeText = "";
    private FileType fileType;
    private TemplateCommentPanel parentPanel;
    private JRadioButton[] fileLocations = null;

    private FileTypeOverridePanel ftOptionsPanel = null;
    private TemplateOptionsPanel tempOptionsPanel;
    private JTextArea noticeBody;
    private JPanel mainPanel;
    private JCheckBox cbPreview;
    private JScrollPane noticeScroll;
    private JButton btnValidate;
    private JTextField txtKeyword;
    private JButton btnSelect;
    private JLabel lblKeyword;
    private JPanel overridePanel;
    private JRadioButton rbBefore;
    private JRadioButton rbAfter;
    private JPanel fileLocationPanel;
    private JCheckBox cbAddBlank;
    private JCheckBox cbUseAlternate;

}