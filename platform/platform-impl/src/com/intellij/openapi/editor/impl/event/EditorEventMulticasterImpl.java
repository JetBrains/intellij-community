// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeListener;
import java.util.*;

public final class EditorEventMulticasterImpl implements EditorEventMulticasterEx {
  private static final ExtensionPointName<EditorMouseListener> MOUSE_EP = new ExtensionPointName<>("com.intellij.editorFactoryMouseListener");
  private static final ExtensionPointName<EditorMouseMotionListener> MOUSE_MOTION_EP = new ExtensionPointName<>("com.intellij.editorFactoryMouseMotionListener");
  private static final ExtensionPointName<DocumentListener> DOCUMENT_EP = new ExtensionPointName<>("com.intellij.editorFactoryDocumentListener");

  private final EventDispatcher<DocumentListener> myDocumentMulticaster = EventDispatcher.create(DocumentListener.class);
  private final EventDispatcher<PrioritizedDocumentListener> myPrioritizedDocumentMulticaster = EventDispatcher.create(PrioritizedDocumentListener.class, Collections.singletonMap("getPriority", EditorDocumentPriorities.RANGE_MARKER));
  private final EventDispatcher<EditReadOnlyListener> myEditReadOnlyMulticaster = EventDispatcher.create(EditReadOnlyListener.class);

  private final EventDispatcher<EditorMouseListener> myEditorMouseMulticaster = EventDispatcher.create(EditorMouseListener.class);
  private final EventDispatcher<EditorMouseMotionListener> myEditorMouseMotionMulticaster = EventDispatcher.create(EditorMouseMotionListener.class);
  private final EventDispatcher<ErrorStripeListener> myErrorStripeMulticaster = EventDispatcher.create(ErrorStripeListener.class);
  private final EventDispatcher<CaretListener> myCaretMulticaster = EventDispatcher.create(CaretListener.class);
  private final EventDispatcher<SelectionListener> mySelectionMulticaster = EventDispatcher.create(SelectionListener.class);
  private final EventDispatcher<VisibleAreaListener> myVisibleAreaMulticaster = EventDispatcher.create(VisibleAreaListener.class);
  private final EventDispatcher<PropertyChangeListener> myPropertyChangeMulticaster = EventDispatcher.create(PropertyChangeListener.class);
  private final EventDispatcher<FocusChangeListener> myFocusChangeListenerMulticaster = EventDispatcher.create(FocusChangeListener.class);

  public void registerDocument(@NotNull DocumentEx document) {
    document.addDocumentListener(myDocumentMulticaster.getMulticaster());
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent event) {
        DOCUMENT_EP.forEachExtensionSafe(it -> it.beforeDocumentChange(event));
      }

      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        DOCUMENT_EP.forEachExtensionSafe(it -> it.documentChanged(event));
      }

      @Override
      public void bulkUpdateStarting(@NotNull Document document) {
        DOCUMENT_EP.forEachExtensionSafe(it -> it.bulkUpdateStarting(document));
      }

      @Override
      public void bulkUpdateFinished(@NotNull Document document) {
        DOCUMENT_EP.forEachExtensionSafe(it -> it.bulkUpdateFinished(document));
      }
    });
    document.addDocumentListener(myPrioritizedDocumentMulticaster.getMulticaster());
    document.addEditReadOnlyListener(myEditReadOnlyMulticaster.getMulticaster());
  }

  public void registerEditor(@NotNull EditorEx editor) {
    editor.addEditorMouseListener(myEditorMouseMulticaster.getMulticaster());
    editor.addEditorMouseListener(new EditorMouseListener() {
      @Override
      public void mousePressed(@NotNull EditorMouseEvent event) {
        MOUSE_EP.forEachExtensionSafe(it -> it.mousePressed(event));
      }

      @Override
      public void mouseClicked(@NotNull EditorMouseEvent event) {
        MOUSE_EP.forEachExtensionSafe(it -> it.mouseClicked(event));
      }

      @Override
      public void mouseReleased(@NotNull EditorMouseEvent event) {
        MOUSE_EP.forEachExtensionSafe(it -> it.mouseReleased(event));
      }

      @Override
      public void mouseEntered(@NotNull EditorMouseEvent event) {
        MOUSE_EP.forEachExtensionSafe(it -> it.mouseEntered(event));
      }

      @Override
      public void mouseExited(@NotNull EditorMouseEvent event) {
        MOUSE_EP.forEachExtensionSafe(it -> it.mouseExited(event));
      }
    });

    editor.addEditorMouseMotionListener(myEditorMouseMotionMulticaster.getMulticaster());
    editor.addEditorMouseMotionListener(new EditorMouseMotionListener() {
      @Override
      public void mouseMoved(@NotNull EditorMouseEvent event) {
        MOUSE_MOTION_EP.forEachExtensionSafe(it -> it.mouseMoved(event));
      }

      @Override
      public void mouseDragged(@NotNull EditorMouseEvent event) {
        MOUSE_MOTION_EP.forEachExtensionSafe(it -> it.mouseDragged(event));
      }
    });

    ((EditorMarkupModel) editor.getMarkupModel()).addErrorMarkerListener(myErrorStripeMulticaster.getMulticaster(), ((EditorImpl)editor).getDisposable());
    editor.getCaretModel().addCaretListener(myCaretMulticaster.getMulticaster());
    editor.getSelectionModel().addSelectionListener(mySelectionMulticaster.getMulticaster());
    editor.getScrollingModel().addVisibleAreaListener(myVisibleAreaMulticaster.getMulticaster());
    editor.addPropertyChangeListener(myPropertyChangeMulticaster.getMulticaster());
    editor.addFocusListener(myFocusChangeListenerMulticaster.getMulticaster());
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener) {
    myDocumentMulticaster.addListener(listener);
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) {
    myDocumentMulticaster.addListener(listener, parentDisposable);
  }

  /**
   * Dangerous method.
   * When high-priority listener fires, the underlying subsystems (e.g., folding, caret, etc.) may not be ready yet.
   * So all requests to the e.g., caret offset might generate exceptions.
   * Use for internal purposes only.
   * @see EditorDocumentPriorities
   */
  public void addPrioritizedDocumentListener(@NotNull PrioritizedDocumentListener listener, @NotNull Disposable parent) {
    myPrioritizedDocumentMulticaster.addListener(listener, parent);
  }

  @Override
  public void removeDocumentListener(@NotNull DocumentListener listener) {
    myDocumentMulticaster.removeListener(listener);
  }

  @Override
  public void addEditorMouseListener(@NotNull EditorMouseListener listener) {
    myEditorMouseMulticaster.addListener(listener);
  }

  @Override
  public void addEditorMouseListener(@NotNull EditorMouseListener listener, @NotNull Disposable parentDisposable) {
    myEditorMouseMulticaster.addListener(listener, parentDisposable);
  }

  @Override
  public void removeEditorMouseListener(@NotNull EditorMouseListener listener) {
    myEditorMouseMulticaster.removeListener(listener);
  }

  @Override
  public void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
    myEditorMouseMotionMulticaster.addListener(listener);
  }

  @Override
  public void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener, @NotNull Disposable parentDisposable) {
    myEditorMouseMotionMulticaster.addListener(listener, parentDisposable);
  }

  @Override
  public void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
    myEditorMouseMotionMulticaster.removeListener(listener);
  }

  @Override
  public void addCaretListener(@NotNull CaretListener listener) {
    myCaretMulticaster.addListener(listener);
  }

  @Override
  public void addCaretListener(@NotNull CaretListener listener, @NotNull Disposable parentDisposable) {
    myCaretMulticaster.addListener(listener, parentDisposable);
  }

  @Override
  public void removeCaretListener(@NotNull CaretListener listener) {
    myCaretMulticaster.removeListener(listener);
  }

  @Override
  public void addSelectionListener(@NotNull SelectionListener listener) {
    mySelectionMulticaster.addListener(listener);
  }

  @Override
  public void addSelectionListener(@NotNull SelectionListener listener, @NotNull Disposable parentDisposable) {
    mySelectionMulticaster.addListener(listener, parentDisposable);
  }

  @Override
  public void removeSelectionListener(@NotNull SelectionListener listener) {
    mySelectionMulticaster.removeListener(listener);
  }

  @Override
  public void addErrorStripeListener(@NotNull ErrorStripeListener listener, @NotNull Disposable parentDisposable) {
    myErrorStripeMulticaster.addListener(listener, parentDisposable);
  }

  @Override
  public void addVisibleAreaListener(@NotNull VisibleAreaListener listener) {
    myVisibleAreaMulticaster.addListener(listener);
  }

  @Override
  public void addVisibleAreaListener(@NotNull VisibleAreaListener listener, @NotNull Disposable parent) {
    myVisibleAreaMulticaster.addListener(listener, parent);
  }

  @Override
  public void removeVisibleAreaListener(@NotNull VisibleAreaListener listener) {
    myVisibleAreaMulticaster.removeListener(listener);
  }

  @Override
  public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener, @NotNull Disposable parentDisposable) {
    myEditReadOnlyMulticaster.addListener(listener, parentDisposable);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parentDisposable) {
    myPropertyChangeMulticaster.addListener(listener, parentDisposable);
  }

  @Override
  public void addFocusChangeListener(@NotNull FocusChangeListener listener, @NotNull Disposable parentDisposable) {
    myFocusChangeListenerMulticaster.addListener(listener,parentDisposable);
  }

  @TestOnly
  public Map<Class<? extends EventListener>, List<? extends EventListener>> getListeners() {
    Map<Class<? extends EventListener>, List<? extends EventListener>> myCopy = new LinkedHashMap<>();
    myCopy.put(DocumentListener.class, new ArrayList<>(myDocumentMulticaster.getListeners()));
    myCopy.put(EditReadOnlyListener.class, new ArrayList<>(myEditReadOnlyMulticaster.getListeners()));

    myCopy.put(EditorMouseListener.class, new ArrayList<>(myEditorMouseMulticaster.getListeners()));
    myCopy.put(EditorMouseMotionListener.class, new ArrayList<>(myEditorMouseMotionMulticaster.getListeners()));
    myCopy.put(ErrorStripeListener.class, new ArrayList<>(myErrorStripeMulticaster.getListeners()));
    myCopy.put(CaretListener.class, new ArrayList<>(myCaretMulticaster.getListeners()));
    myCopy.put(SelectionListener.class, new ArrayList<>(mySelectionMulticaster.getListeners()));
    myCopy.put(VisibleAreaListener.class, new ArrayList<>(myVisibleAreaMulticaster.getListeners()));
    myCopy.put(PropertyChangeListener.class, new ArrayList<>(myPropertyChangeMulticaster.getListeners()));
    myCopy.put(FocusChangeListener.class, new ArrayList<>(myFocusChangeListenerMulticaster.getListeners()));
    return myCopy;
  }
}
