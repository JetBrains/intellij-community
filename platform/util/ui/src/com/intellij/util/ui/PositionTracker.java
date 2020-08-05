// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.*;

public abstract class PositionTracker<T> implements Disposable, HierarchyBoundsListener, HierarchyListener, ComponentListener {
  private final Component myComponent;
  private Client<T> myClient;

  public PositionTracker(Component component) {
    myComponent = component;
  }

  public final void init(Client<T> client) {
    myClient = client;

    Disposer.register(client, this);

    myComponent.addHierarchyBoundsListener(this);
    myComponent.addHierarchyListener(this);
    myComponent.addComponentListener(this);
  }

  public final Component getComponent() {
    return myComponent;
  }

  @Override
  public final void ancestorMoved(HierarchyEvent e) {
    revalidate();
  }

  @Override
  public final void ancestorResized(HierarchyEvent e) {
    revalidate();
  }

  @Override
  public final void hierarchyChanged(HierarchyEvent e) {
    revalidate();
  }

  @Override
  public void componentResized(ComponentEvent e) {
    revalidate();
  }

  @Override
  public void componentMoved(ComponentEvent e) {
    revalidate();
  }

  @Override
  public void componentShown(ComponentEvent e) {
    revalidate();
  }

  @Override
  public void componentHidden(ComponentEvent e) {
    revalidate();
  }

  protected final void revalidate() {
    myClient.revalidate(this);
  }

  public abstract RelativePoint recalculateLocation(@NotNull T object);

  @Override
  public final void dispose() {
    myComponent.removeHierarchyBoundsListener(this);
    myComponent.removeHierarchyListener(this);
    myComponent.removeComponentListener(this);
  }

  public static final class Static<T> extends PositionTracker<T> {

    private final RelativePoint myPoint;

    public Static(RelativePoint point) {
      super(point.getComponent());
      myPoint = point;
    }

    @Override
    public RelativePoint recalculateLocation(@NotNull Object object) {
      return myPoint;
    }
  }

  public interface Client<T> extends Disposable {

    void revalidate();
    void revalidate(@NotNull PositionTracker<T> tracker);

  }

}
