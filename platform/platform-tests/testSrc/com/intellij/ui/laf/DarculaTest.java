// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.laf;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ui.ShowUIDefaultsAction;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.JBTable;
import com.intellij.util.TimeoutUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("HardCodedStringLiteral")
public class DarculaTest {
  private JBCheckBox myJBCheckBox1;
  private JBCheckBox myJBCheckBox2;
  private JBCheckBox myJBCheckBox3;
  private JBCheckBox myJBCheckBox4;
  private JBCheckBox myJBCheckBox5;
  private JComboBox myComboBox1;
  private JComboBox myComboBox2;
  private JComboBox myComboBox3;
  private JComboBox myComboBox4;
  private JComboBox myComboBox5;
  private JTextField myTextField1;
  private JTextField myThisTextIsDisabledTextField;
  private JPasswordField myPasswordField1;
  private JPanel myRoot;
  private JButton myHelpButton;
  private JButton myCancelButton;
  private JButton myDisabledButton;
  private JButton myDefaultButton;
  private JTextField myTextField2;
  private JTextField myTextField3;
  private JTextField myTextField4;
  private JSpinner mySpinner1;
  private JProgressBar myProgressBar1;
  private JButton myProgressButton;
  private JProgressBar myProgressBar2;
  private JButton myStartButton;
  private JBTable myTable;

  public DarculaTest() {
    myProgressButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myProgressButton.getText().equals("Start")) {
          myProgressBar1.setIndeterminate(true);
          myProgressButton.setText("Stop");
        } else {
          myProgressBar1.setIndeterminate(false);
          myProgressButton.setText("Start");
        }
      }
    });
    myStartButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myStartButton.setEnabled(false);
        new Thread("darcula test"){
          @Override
          public void run() {
            while (myProgressBar2.getValue() < 100) {
              TimeoutUtil.sleep(20);
              myProgressBar2.setValue(myProgressBar2.getValue() + 1);
            }

            TimeoutUtil.sleep(1000);

            myProgressBar2.setValue(0);
            myStartButton.setEnabled(true);
          }
        }.start();
      }
    });

    myTable.setModel(new DefaultTableModel(new Object[][]{{"Test", "Darcula"}, {"Test1", "Darcula1"}}, new Object[]{"Name", "Value"}));
  }

  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(new DarculaLaf());
    }
    catch (UnsupportedLookAndFeelException ignored) {}
    final JFrame frame = new JFrame("Darcula Demo");
    frame.setSize(900, 500);
    final DarculaTest form = new DarculaTest();
    final JPanel root = form.myRoot;
    frame.setContentPane(root);
    frame.getRootPane().setDefaultButton(form.myDefaultButton);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
      @Override
      public void eventDispatched(AWTEvent event) {
        if (event instanceof KeyEvent && event.getID() == KeyEvent.KEY_PRESSED && ((KeyEvent)event).getKeyCode() == KeyEvent.VK_F1) {
          new ShowUIDefaultsAction().perform(null);
        }
      }
    }, AWTEvent.KEY_EVENT_MASK);
    SwingUtilities.invokeLater(() -> frame.setVisible(true));
  }
}
