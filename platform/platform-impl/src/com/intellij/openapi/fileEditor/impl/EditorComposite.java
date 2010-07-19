/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.ui.PrevNextActionsDescriptor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.tabs.UiDecorator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * This class hides internal structure of UI component which represent
 * set of opened editors. For example, one myEditor is represented by its
 * component, more then one myEditor is wrapped into tabbed pane.
 *
 * @author Vladimir Kondratyev
 */
public abstract class EditorComposite implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorComposite");

  /**
   * File for which composite is created
   */
  private final VirtualFile myFile;
  /**
   * Whether the composite is pinned or not
   */
  private boolean myPinned;
  /**
   * Editors which are opened in the composite
   */
  protected final FileEditor[] myEditors;
  /**
   * This is initial timestamp of the file. It uses to implement
   * "close non modified editors first" feature.
   */
  private final long myInitialFileTimeStamp;
  protected final TabbedPaneWrapper myTabbedPaneWrapper;
  private final MyComponent myComponent;
  private final FocusWatcher myFocusWatcher;
  /**
   * Currently selected myEditor
   */
  private FileEditor mySelectedEditor;
  private final FileEditorManagerEx myFileEditorManager;
  private final long myInitialFileModificationStamp;
  private final Map<FileEditor, JComponent> myTopComponents = new HashMap<FileEditor, JComponent>();
  private final Map<FileEditor, JComponent> myBottomComponents = new HashMap<FileEditor, JComponent>();

  /**
   * @param file <code>file</code> for which composite is being constructed
   *
   * @param editors <code>edittors</code> that should be placed into the composite
   *
   * @exception java.lang.IllegalArgumentException if <code>editors</code>
   * is <code>null</code>
   *
   * @exception java.lang.IllegalArgumentException if <code>providers</code>
   * is <code>null</code>
   *
   * @exception java.lang.IllegalArgumentException if <code>myEditor</code>
   * arrays is empty
   */
  EditorComposite(
    @NotNull final VirtualFile file,
    @NotNull final FileEditor[] editors,
    @NotNull final FileEditorManagerEx fileEditorManager
  ){
    myFile = file;
    myEditors = editors;
    myFileEditorManager = fileEditorManager;
    myInitialFileTimeStamp     = myFile.getTimeStamp();
    myInitialFileModificationStamp = myFile.getModificationStamp();

    Disposer.register(fileEditorManager.getProject(), this);

    if(editors.length > 1){
      PrevNextActionsDescriptor descriptor = new PrevNextActionsDescriptor(IdeActions.ACTION_NEXT_EDITOR_TAB, IdeActions.ACTION_PREVIOUS_EDITOR_TAB);
      final TabbedPaneWrapper.AsJBTabs wrapper = new TabbedPaneWrapper.AsJBTabs(fileEditorManager.getProject(), SwingConstants.BOTTOM, descriptor, this);
      wrapper.getTabs().getPresentation().setPaintBorder(0, 0, 0, 0).setTabSidePaintBorder(1).setGhostsAlwaysVisible(true).setUiDecorator(new UiDecorator() {
        @NotNull
        public UiDecoration getDecoration() {
          return new UiDecoration(null, new Insets(0, 8, 0, 8));
        }
      });
      wrapper.getTabs().getComponent().setBorder(new EmptyBorder(0, 0, 1, 0));

      myTabbedPaneWrapper=wrapper;
      myComponent=new MyComponent(wrapper.getComponent()){
        public boolean requestFocusInWindow() {
          return wrapper.getComponent().requestFocusInWindow();
        }

        public void requestFocus() {
          wrapper.getComponent().requestFocus();
        }

        public boolean requestDefaultFocus() {
          return wrapper.getComponent().requestDefaultFocus();
        }
      };
      for (FileEditor editor : editors) {
        wrapper.addTab(editor.getName(), createEditorComponent(editor));
      }
      myTabbedPaneWrapper.addChangeListener(new MyChangeListener());
    }
    else if(editors.length==1){
      myTabbedPaneWrapper=null;
      myComponent = new MyComponent(createEditorComponent(editors[0])){
        public void requestFocus() {
          JComponent component = editors[0].getPreferredFocusedComponent();
          if (component != null) {
            component.requestFocus();
          }
        }

        public boolean requestFocusInWindow() {
          JComponent component = editors[0].getPreferredFocusedComponent();
          if (component != null) {
            return component.requestFocusInWindow();
          }

          return false;
        }

        public boolean requestDefaultFocus() {
          JComponent component = editors[0].getPreferredFocusedComponent();
          if (component != null) {
            return component.requestDefaultFocus();
          }
          return false;
        }
      };
    }
    else{
      throw new IllegalArgumentException("editors array cannot be empty");
    }

    mySelectedEditor = editors[0];
    myFocusWatcher = new FocusWatcher();
    myFocusWatcher.install(myComponent);

    myFileEditorManager.addFileEditorManagerListener(new FileEditorManagerAdapter() {
      @Override
      public void selectionChanged(final FileEditorManagerEvent event) {
        final VirtualFile oldFile = event.getOldFile();
        final VirtualFile newFile = event.getNewFile();
        if (oldFile == newFile && getFile() == newFile) {
          final FileEditor oldEditor = event.getOldEditor();
          if (oldEditor != null) oldEditor.deselectNotify();
          final FileEditor newEditor = event.getNewEditor();
          if (newEditor != null) newEditor.selectNotify();
        }
      }
    }, this);
  }

  private JComponent createEditorComponent(final FileEditor editor) {
    JPanel component = new JPanel(new BorderLayout());
    JComponent comp = editor.getComponent();
    if (!FileEditorManagerImpl.isDumbAware(editor)) {
      comp = DumbService.getInstance(myFileEditorManager.getProject()).wrapGently(comp, editor);
    }

    component.add(comp, BorderLayout.CENTER);

    JPanel topPanel = new TopBottomPanel();
    myTopComponents.put(editor, topPanel);
    component.add(topPanel, BorderLayout.NORTH);

    final JPanel bottomPanel = new TopBottomPanel();
    myBottomComponents.put(editor, bottomPanel);
    component.add(bottomPanel, BorderLayout.SOUTH);

    return component;
  }

  /**
   * @return whether myEditor composite is pinned
   */
  public boolean isPinned(){
    return myPinned;
  }

  /**
   * Sets new "pinned" state
   */
  void setPinned(final boolean pinned){
    myPinned = pinned;
  }

  private void fireSelectedEditorChanged(final FileEditor oldSelectedEditor, final FileEditor newSelectedEditor){
    if (!myFileEditorManager.isInsideChange() && !Comparing.equal(oldSelectedEditor, newSelectedEditor)) {
      final FileEditorManagerEvent event = new FileEditorManagerEvent(myFileEditorManager, myFile, oldSelectedEditor, myFile, newSelectedEditor);
      final FileEditorManagerListener publisher = myFileEditorManager.getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);
      publisher.selectionChanged(event);
    }
  }


  /**
   * @return preferred focused component inside myEditor composite. Composite uses FocusWatcher to
   * track focus movement inside the myEditor.
   */
  JComponent getPreferredFocusedComponent(){
    final Component component = myFocusWatcher.getFocusedComponent();
    if(!(component instanceof JComponent) || !component.isShowing() || !component.isEnabled() || !component.isFocusable()){
      return getSelectedEditor().getPreferredFocusedComponent();
    }
    return (JComponent)component;
  }

  /**
   * @return file for which composite was created. The method always
   * returns not <code>null</code> valus.
   */
  public VirtualFile getFile() {
    return myFile;
  }

  public FileEditorManager getFileEditorManager() {
    return myFileEditorManager;
  }

  /**
   * @return initial time stamp of the file (on moment of creation of
   * the composite)
   */
  public long getInitialFileTimeStamp() {
    return myInitialFileTimeStamp;
  }

  /**
   * @return initial modifcation stamp of the file (on moment of creation of
   * the composite)
   */
  public long getInitialFileModificationStamp() {
    return myInitialFileModificationStamp;
  }

  /**
   * @return editors which are opened in the composite. <b>Do not modify
   * this array</b>.
   */
  public FileEditor[] getEditors() {
    return myEditors;
  }

  public void addTopComponent(FileEditor editor, JComponent component) {
    manageTopOrBottomComponent(editor, component, true, false);
  }

  public void removeTopComponent(FileEditor editor, JComponent component) {
    manageTopOrBottomComponent(editor, component, true, true);
  }

  public void addBottomComponent(FileEditor editor, JComponent component) {
    manageTopOrBottomComponent(editor, component, false, false);
  }

  public void removeBottomComponent(FileEditor editor, JComponent component) {
    manageTopOrBottomComponent(editor, component, false, true);
  }

  private void manageTopOrBottomComponent(FileEditor editor, JComponent component, boolean top, boolean remove) {
    final JComponent container = top ? myTopComponents.get(editor) : myBottomComponents.get(editor);
    assert container != null;

    if (remove) {
      container.remove(component.getParent());
    } else {
      container.add(new TopBottomComponentWrapper(component, top));
    }
    container.revalidate();
  }

  /**
   * @return currently selected myEditor. The method never returns <code>null</code>.
   */
  @NotNull FileEditor getSelectedEditor(){
    return getSelectedEditorWithProvider ().getFirst ();
  }

  public boolean isDisposed() {
    return myTabbedPaneWrapper != null && myTabbedPaneWrapper.isDisposed();
  }

  /**
   * @return currently selected myEditor with its provider. The method never returns <code>null</code>.
   */
  public abstract Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider();

  void setSelectedEditor(final int index){
    if(myEditors.length == 1){
      // nothing to do
      LOG.assertTrue(myTabbedPaneWrapper == null);
    }
    else{
      LOG.assertTrue(myTabbedPaneWrapper != null);
      myTabbedPaneWrapper.setSelectedIndex(index);
    }
  }

  /**
   * @return component which represents set of file editors in the UI
   */
  public JComponent getComponent() {
    return myComponent;
  }

  /**
   * @return <code>true</code> if the composite contains at least one
   * modified myEditor
   */
  public boolean isModified(){
    for(int i=myEditors.length-1;i>=0;i--){
      if(myEditors[i].isModified()){
        return true;
      }
    }
    return false;
  }

  /**
   * Handles changes of selected myEditor
   */
  private final class MyChangeListener implements ChangeListener{
    public void stateChanged(ChangeEvent e) {
      FileEditor oldSelectedEditor = mySelectedEditor;
      LOG.assertTrue(oldSelectedEditor != null);
      int selectedIndex = myTabbedPaneWrapper.getSelectedIndex();
      LOG.assertTrue(selectedIndex != -1);
      mySelectedEditor = myEditors[selectedIndex];
      fireSelectedEditorChanged(oldSelectedEditor, mySelectedEditor);
    }
  }

  private abstract class MyComponent extends JPanel implements DataProvider{
    public MyComponent(JComponent realComponent){
      super(new BorderLayout());
      add(realComponent, BorderLayout.CENTER);
    }

    public final Object getData(String dataId){
      if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
        return getSelectedEditor();
      }
      else if(PlatformDataKeys.VIRTUAL_FILE.is(dataId)){
        return myFile.isValid() ? myFile : null;
      }
      else if(PlatformDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)){
        return myFile.isValid() ? new VirtualFile[] {myFile} : null;
      }
      else{
        JComponent component = getPreferredFocusedComponent();
        if(component instanceof DataProvider && component != this){
          return ((DataProvider)component).getData(dataId);
        }
        else{
          return null;
        }
      }
    }
  }

  public void dispose() {
  }

  private class TopBottomPanel extends JPanel {
    private TopBottomPanel() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    @Override
    public Color getBackground() {
      Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.GUTTER_BACKGROUND);
      return color == null ? Color.gray : color;
    }
  }

  private class TopBottomComponentWrapper extends JPanel {
    public TopBottomComponentWrapper(JComponent component, boolean top) {
      super(new BorderLayout());
      setOpaque(false);

      setBorder(new SideBorder(null, top ? SideBorder.BOTTOM : SideBorder.TOP, true) {
        @Override
        public Color getLineColor() {
          Color result = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.TEARLINE_COLOR);
          return result == null ? Color.black : result;
        }
      });
      
      add(component);
    }
  }
}
