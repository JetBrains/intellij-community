/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.treeStructure.Tree;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class JBTabsDemo {
  public static void main(String[] args) {
    System.out.println("JBTabs.main");

    IconLoader.activate();

    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout(0, 0));
    final int[] count = new int[1];
    final JBTabsImpl tabs = new JBTabsImpl(null, ActionManager.getInstance(), null, Disposer.newDisposable());
    tabs.setTestMode(true);


    //final JPanel flow = new JPanel(new FlowLayout(FlowLayout.CENTER));
    //frame.getContentPane().add(flow);
    //flow.add(tabs.getComponent());

    frame.getContentPane().add(tabs.getComponent(), BorderLayout.CENTER);

    JPanel south = new JPanel(new FlowLayout());
    south.setOpaque(true);
    south.setBackground(Color.white);


    final JComboBox pos = new JComboBox(new Object[]{JBTabsPosition.top, JBTabsPosition.left, JBTabsPosition.right, JBTabsPosition.bottom});
    pos.setSelectedIndex(0);
    south.add(pos);
    pos.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final JBTabsPosition p = (JBTabsPosition)pos.getSelectedItem();
        if (p != null) {
          tabs.getPresentation().setTabsPosition(p);
        }
      }
    });

    final JCheckBox bb = new JCheckBox("Buffered", true);
    bb.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setUseBufferedPaint(bb.isSelected());
      }
    });
    south.add(bb);

    final JCheckBox f = new JCheckBox("Focused");
    f.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setFocused(f.isSelected());
      }
    });
    south.add(f);


    final JCheckBox v = new JCheckBox("Vertical");
    v.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setSideComponentVertical(v.isSelected());
      }
    });
    south.add(v);

    final JCheckBox before = new JCheckBox("Before", true);
    before.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setSideComponentBefore(before.isSelected());
      }
    });
    south.add(before);

    final JCheckBox row = new JCheckBox("Single row", true);
    row.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setSingleRow(row.isSelected());
      }
    });
    south.add(row);

    final JCheckBox ghosts = new JCheckBox("Ghosts always visible", false);
    ghosts.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setGhostsAlwaysVisible(ghosts.isSelected());
      }
    });
    south.add(ghosts);

    final JCheckBox stealth = new JCheckBox("Stealth tab", tabs.isStealthTabMode());
    stealth.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setStealthTabMode(stealth.isSelected());
      }
    });
    south.add(stealth);

    final JCheckBox hide = new JCheckBox("Hide tabs", tabs.isHideTabs());
    hide.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        tabs.setHideTabs(hide.isSelected());
      }
    });
    south.add(hide);

    frame.getContentPane().add(south, BorderLayout.SOUTH);

    tabs.addListener(new TabsListener.Adapter() {
      public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
        System.out.println("TabsWithActions.selectionChanged old=" + oldSelection + " new=" + newSelection);
      }
    });

    final JTree someTree = new Tree() {
      public void addNotify() {
        super.addNotify();
        System.out.println("JBTabs.addNotify");
      }

      public void removeNotify() {
        System.out.println("JBTabs.removeNotify");
        super.removeNotify();
      }
    };
    //someTree.setBorder(new LineBorder(Color.cyan));
    tabs.addTab(new TabInfo(someTree)).setText("Tree1").setActions(new DefaultActionGroup(), null)
        .setIcon(AllIcons.Debugger.Frame);

    final JTree component = new Tree();
    final TabInfo toAnimate1 = new TabInfo(component);
    //toAnimate1.setIcon(IconLoader.getIcon("/debugger/console.png"));
    final JCheckBox attract1 = new JCheckBox("Attract 1");
    attract1.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        //toAnimate1.setText("Should be animated");

        if (attract1.isSelected()) {
          toAnimate1.fireAlert();
        }
        else {
          toAnimate1.stopAlerting();
        }
      }
    });
    south.add(attract1);

    final JCheckBox hide1 = new JCheckBox("Hide 1", toAnimate1.isHidden());
    hide1.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        toAnimate1.setHidden(!toAnimate1.isHidden());
      }
    });
    south.add(hide1);


    final JCheckBox block = new JCheckBox("Block", false);
    block.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        tabs.setPaintBlocked(!block.isSelected(), true);
      }
    });
    south.add(block);

    final JCheckBox fill = new JCheckBox("Tab fill in", true);
    fill.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        tabs.getPresentation().setActiveTabFillIn(fill.isSelected() ? Color.white : null);
      }
    });
    south.add(fill);


    final JButton refire = new JButton("Re-fire attraction");
    refire.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        toAnimate1.fireAlert();
      }
    });

    south.add(refire);




    final JEditorPane text = new JEditorPane();
    text.setEditorKit(new HTMLEditorKit());
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < 50; i ++) {
      buffer.append("1234567890abcdefghijklmnopqrstv1234567890abcdefghijklmnopqrstv1234567890abcdefghijklmnopqrstv<br>");
    }
    text.setText(buffer.toString());

    final JLabel tb = new JLabel("Side comp");
    tb.setBorder(new LineBorder(Color.red));
    tabs.addTab(new TabInfo(ScrollPaneFactory.createScrollPane(text)).setSideComponent(tb)).setText("Text text text");
    tabs.addTab(toAnimate1).append("Tree2", new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, Color.black, Color.red));
    tabs.addTab(new TabInfo(new JTable())).setText("Table 1").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 2").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 3").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 4").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 5").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 6").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 7").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 8").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 9").setActions(new DefaultActionGroup(), null);

    //tabs.getComponent().setBorder(new EmptyBorder(5, 5, 5, 5));
    tabs.setTabSidePaintBorder(5);
    tabs.setPaintBorder(1, 1, 1, 1);

    tabs.getPresentation().setActiveTabFillIn(Color.white);
    tabs.setGhostsAlwaysVisible(true);

    //tabs.setBorder(new LineBorder(Color.blue, 5));
    tabs.setBorder(new EmptyBorder(30, 30, 30, 30));

    tabs.setUiDecorator(new UiDecorator() {
      public UiDecoration getDecoration() {
        return new UiDecoration(null, new Insets(0, -1, 0, -1));
      }
    });


    tabs.setStealthTabMode(true);

    frame.setBounds(1400, 200, 1000, 800);
    frame.show();


  }

}
