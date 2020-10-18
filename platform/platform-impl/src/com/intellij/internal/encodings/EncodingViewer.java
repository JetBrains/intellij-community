// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.encodings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class EncodingViewer extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(EncodingViewer.class);

  private JPanel myPanel;
  private JTextArea myText;
  private JComboBox myEncoding;
  private JButton myLoadFile;
  private byte[] myBytes;

  public EncodingViewer() {
    super(false);
    initEncodings();
    myLoadFile.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        VirtualFile file = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(), myPanel, null, null);
        if (file != null) {
          loadFrom(file);
        }
      }
    });
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "EncodingViewer";
  }

  private void loadFrom(VirtualFile virtualFile) {
    try {
      myBytes = virtualFile.contentsToByteArray();
    }
    catch (IOException e) {
      LOG.error(e);
      return;
    }
    refreshText();
  }

  private void refreshText() {
    if (myBytes == null) return;
    try {
      String selectedCharset = getSelectedCharset();
      myText.setText(new String(myBytes, selectedCharset));
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
    }
  }

  private @NotNull String getSelectedCharset() {
    return ((Charset) myEncoding.getSelectedItem()).name();
  }

  private void initEncodings() {
    Charset[] availableCharsets = CharsetToolkit.getAvailableCharsets();
    myEncoding.setModel(new DefaultComboBoxModel(availableCharsets));
    int defaultIndex = Arrays.asList(availableCharsets).indexOf(CharsetToolkit.getDefaultSystemCharset());
    myEncoding.setSelectedIndex(defaultIndex);
    myEncoding.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          refreshText();
        }
      }
    });
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
