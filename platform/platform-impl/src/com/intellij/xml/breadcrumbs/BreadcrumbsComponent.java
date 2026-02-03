// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Weighted;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.*;
import java.util.List;

import static com.intellij.util.ui.SwingTextTrimmer.THREE_DOTS_AT_LEFT;

public final class BreadcrumbsComponent<T extends BreadcrumbsItem> extends JComponent implements Disposable, Weighted {

  private static final Logger LOG = Logger.getInstance(BreadcrumbsComponent.class);
  private static final Painter DEFAULT_PAINTER = new DefaultPainter(new ButtonSettings());

  private static final int EXTRA_WIDTH = 10;

  private List<BreadcrumbsItemListener<T>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private Crumb myHovered;
  private PagedImage myBuffer;
  private List<Crumb> myCrumbs = new ArrayList<>();
  private final CrumbLineMouseListener myMouseListener;
  private List<? extends T> myItems;
  private int myOffset;

  public BreadcrumbsComponent() {
    myMouseListener = new CrumbLineMouseListener(this);
    addMouseListener(myMouseListener);
    addMouseMotionListener(myMouseListener);

    setToolTipText("");
  }

  public void setItems(final @Nullable List<? extends T> itemsList) {
    if (myItems != itemsList) {
      myItems = itemsList;
      myCrumbs = null;
    }

    repaint();
  }

  public void setOffset(int offset) {
    if (myOffset != offset) {
      myOffset = offset;
      repaint();
    }
  }

  public void addBreadcrumbsItemListener(final @NotNull BreadcrumbsItemListener<T> listener) {
    myListeners.add(listener);
  }

  public void removeBreadcrumbsItemListener(final @NotNull BreadcrumbsItemListener<T> listener) {
    myListeners.remove(listener);
  }

  @Override
  public String getToolTipText(final MouseEvent event) {
    final Crumb c = getCrumb(event.getPoint());
    if (c != null) {
      final String text = c.getTooltipText();
      return text == null ? super.getToolTipText(event) : text;
    }

    return super.getToolTipText(event);
  }

  public @Nullable Crumb getCrumb(final @NotNull Point p) {
    if (myCrumbs != null) {
      final Rectangle r = getBounds();
      p.translate(r.x, r.y);
      
      if (!r.contains(p)) {
        return null;
      }

      if (myBuffer == null) {
        return null;
      }

      final int offset = myBuffer.getPageOffset();

      for (final Crumb each : myCrumbs) {
        if (p.x + offset >= each.getOffset() && p.x + offset < each.getOffset() + each.getWidth()) {
          return each;
        }
      }
    }

    return null;
  }

  public void setHoveredCrumb(final @Nullable Crumb crumb) {
    if (crumb != null) {
      crumb.setHovered(true);
    }

    if (myHovered != null) {
      myHovered.setHovered(false);
    }

    myHovered = crumb;
    for (BreadcrumbsItemListener listener : myListeners) {
      listener.itemHovered(myHovered != null ? myHovered.myItem : null);
    }
    repaint();
  }

  public void nextPage() {
    if (myBuffer != null) {
      final int page = myBuffer.getPage();
      if (page + 1 < myBuffer.getPageCount()) {
        myBuffer.setPage(page + 1);
      }
    }

    repaint();
  }

  public void previousPage() {
    if (myBuffer != null) {
      final int page = myBuffer.getPage();
      if (page - 1 >= 0) {
        myBuffer.setPage(page - 1);
      }
    }

    repaint();
  }

  @Override
  public void paint(final Graphics g) {
    final Graphics2D g2 = (Graphics2D)g;
    final Dimension d = getSize();
    final FontMetrics fm = g2.getFontMetrics();

    if (myItems != null) {
      final boolean veryDirty = myCrumbs == null || myBuffer != null && !myBuffer.isValid(d.width);

      final List<Crumb> crumbList = veryDirty ? createCrumbList(fm, myItems, d.width) : myCrumbs;
      if (crumbList != null) {
        if (veryDirty) {
          //final BufferedImage bufferedImage = createBuffer(crumbList, d.height);
          myBuffer = new PagedImage(getTotalWidth(crumbList), d.width);
          myBuffer.setPage(myBuffer.getPageCount() - 1); // point to the last page
        }

        assert myBuffer != null;

        super.paint(g2);

        //if (myDirty) {
        //  myBuffer.repaint(crumbList, getPainter());
        //myDirty = false;
        //}

        myBuffer.paintPage(g2, crumbList, DEFAULT_PAINTER, d.height);
        myCrumbs = crumbList;
      }
    }
    else {
      super.paint(g2);
    }
  }

  private void setSelectedCrumb(final @NotNull Crumb<? extends T> c, final int modifiers) {
    final T selectedElement = c.getItem();

    final Set<BreadcrumbsItem> items = new HashSet<>();
    boolean light = false;
    for (final Crumb each : myCrumbs) {
      final BreadcrumbsItem item = each.getItem();
      if (item != null && items.contains(item)) {
        light = false;
      }

      each.setLight(light);

      if (item != null && !light) {
        items.add(item);
      }

      if (selectedElement == item) {
        each.setSelected(true);
        light = true;
      }
      else {
        each.setSelected(false);
      }
    }

    fireItemSelected(selectedElement, modifiers);

    repaint();
  }

  private void fireItemSelected(final @Nullable T item, final int modifiers) {
    if (item != null) {
      for (BreadcrumbsItemListener listener : myListeners) {
        listener.itemSelected(item, modifiers);
      }
    }
  }

  private @Nullable List<Crumb> createCrumbList(final @NotNull FontMetrics fm, final @NotNull List<? extends T> elements, final int width) {
    if (elements.isEmpty()) {
      return null;
    }

    final LinkedList<Crumb> result = new LinkedList<>();
    int screenWidth = 0;
    Crumb rightmostCrumb = null;

    // fill up crumb list first going from end to start
    final NavigationCrumb fwd = new NavigationCrumb(this, fm, true, DEFAULT_PAINTER);
    for (int i = elements.size() - 1; i >= 0; i--) {
      final NavigationCrumb forward = new NavigationCrumb(this, fm, true, DEFAULT_PAINTER);
      final NavigationCrumb backward = new NavigationCrumb(this, fm, false, DEFAULT_PAINTER);
      final BreadcrumbsItem element = elements.get(i);
      final String s = element.getDisplayText();
      final Dimension d = DEFAULT_PAINTER.getSize(s, fm, width - forward.getWidth() - backward.getWidth());
      final Crumb crumb = new Crumb(this, s, d.width + EXTRA_WIDTH, element);
      if (screenWidth + d.width > width) {
        Crumb first = null;
        if (screenWidth + backward.getWidth() > width && !result.isEmpty()) {
          first = result.removeFirst();
          screenWidth -= first.getWidth();
        }

        // put backward crumb
        result.addFirst(backward);
        screenWidth += backward.getWidth() - myOffset;

        // put dummy crumb to fill up empty space (add it to the end!!!)
        int dummyWidth = width - screenWidth;
        if (dummyWidth > 0) {
          final DummyCrumb dummy = new DummyCrumb(dummyWidth);
          if (rightmostCrumb != null) {
            result.add(result.indexOf(rightmostCrumb) + 1, dummy);
          }
          else {
            result.addLast(dummy);
          }
        }

        // now add forward crumb
        screenWidth = forward.getWidth();
        result.addFirst(forward);

        if (first != null) {
          result.addFirst(first);
          screenWidth += first.getWidth();
        }

        rightmostCrumb = first != null ? first : crumb;
      }

      result.addFirst(crumb);

      screenWidth += d.width;
    }

    if (rightmostCrumb != null && screenWidth < width) {
      // add first dummy crumb
      result.add(result.indexOf(rightmostCrumb) + 2, new DummyCrumb(width - screenWidth - fwd.getWidth() - 8));
    }

    //assert screenWidth < width;

    // now fix up offsets going forward
    int offset = myOffset;
    for (final Crumb each : result) {
      each.setOffset(offset);
      offset += each.getWidth();
    }

    // set selected crumb
    if (!result.isEmpty()) {
      for (int i = result.size() - 1; i >= 0; i--) {
        final Crumb c = result.get(i);
        if (!(c instanceof DummyCrumb)) {
          c.setSelected(true);
          break;
        }
      }
    }

    return result;
  }

  private static int getTotalWidth(final @NotNull List<? extends Crumb> crumbList) {
    int totalWidth = 0;
    for (final Crumb each : crumbList) {
      totalWidth += each.getWidth();
    }

    return totalWidth;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    Graphics2D g2 = (Graphics2D) GraphicsUtil.safelyGetGraphics(this);
    Dimension dim = new Dimension(Integer.MAX_VALUE, g2 != null ? DEFAULT_PAINTER.getSize("DUMMY", g2.getFontMetrics(), Integer.MAX_VALUE).height + 1 : 1);
    JBInsets.addTo(dim, getInsets());
    return dim;
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  public void dispose() {
    removeMouseListener(myMouseListener);
    removeMouseMotionListener(myMouseListener);

    myListeners = null;
  }

  @Override
  public double getWeight() {
    return Double.MAX_VALUE;
  }

  private static final class PagedImage {
    private final int myPageWidth;
    private int myPage;
    private final int myTotalWidth;

    PagedImage(int totalWidth, int pageWidth) {
      myPageWidth = pageWidth;
      myTotalWidth = totalWidth;
    }

    public int getPageCount() {
      if (myTotalWidth < myPageWidth) {
        return 1;
      }

      return myTotalWidth / myPageWidth;
    }

    public void setPage(final int page) {
      assert page >= 0;
      assert page < getPageCount();

      myPage = page;
    }

    public int getPage() {
      return myPage;
    }

    private void repaint(final @NotNull Graphics2D g2,
                         final @NotNull List<? extends Crumb> crumbList,
                         final @NotNull Painter painter,
                         final int height) {
      UISettings.setupAntialiasing(g2);

      //final int height = myImage.getHeight();
      final int pageOffset = getPageOffset();

      for (final Crumb each : crumbList) {
        if (each.getOffset() >= pageOffset && each.getOffset() < pageOffset + myPageWidth) {
          each.paint(g2, painter, height, pageOffset);
        }
      }
    }

    public int getPageOffset() {
      return myPage * myPageWidth;
    }

    public void paintPage(final @NotNull Graphics2D g2, final @NotNull List<? extends Crumb> list, final @NotNull Painter p, final int height) {
      repaint(g2, list, p, height);
    }

    public boolean isValid(final int width) {
      return width == myPageWidth;
    }
  }

  private static final class CrumbLineMouseListener extends MouseAdapter implements MouseMotionListener {
    private final BreadcrumbsComponent myBreadcrumbs;
    private Crumb myHoveredCrumb;

    CrumbLineMouseListener(final @NotNull BreadcrumbsComponent line) {
      myBreadcrumbs = line;
    }

    @Override
    public void mouseDragged(final MouseEvent e) {
      // nothing
    }

    @Override
    public void mouseMoved(final MouseEvent e) {
      final Crumb crumb = myBreadcrumbs.getCrumb(e.getPoint());
      if (crumb != myHoveredCrumb) {
        myBreadcrumbs.setHoveredCrumb(crumb);
        myHoveredCrumb = crumb;
      }
    }

    @Override
    public void mouseExited(final MouseEvent e) {
      mouseMoved(e);
    }

    @Override
    public void mouseEntered(final MouseEvent e) {
      mouseMoved(e);
    }

    @Override
    public void mouseClicked(final MouseEvent e) {
      final Crumb crumb = myBreadcrumbs.getCrumb(e.getPoint());
      if (crumb != null) {
        crumb.performAction(e.getModifiers());
      }
    }
  }

  private static class Crumb<T extends BreadcrumbsItem> {
    private final String myString;
    private int myOffset = -1;
    private final int myWidth;
    private T myItem;
    private BreadcrumbsComponent myLine;
    private boolean mySelected;
    private boolean myHovered;
    private boolean myLight;

    Crumb(final BreadcrumbsComponent line, final String string, final int width, final T item) {
      this(string, width);

      myLine = line;
      myItem = item;
    }

    Crumb(final String string, final int width) {
      myString = string;
      myWidth = width;
    }

    public String getString() {
      return myString;
    }

    public int getOffset() {
      LOG.assertTrue(myOffset != -1, "Negative offet for crumb: " + myString);
      return myOffset;
    }

    public int getWidth() {
      return myWidth;
    }

    public void setOffset(final int offset) {
      myOffset = offset;
    }

    @Override
    public String toString() {
      return getString();
    }

    public void setSelected(final boolean selected) {
      mySelected = selected;
    }

    public void setLight(final boolean light) {
      myLight = light;
    }

    public boolean isHovered() {
      return myHovered;
    }

    public boolean isSelected() {
      return mySelected;
    }

    public boolean isLight() {
      return myLight;
    }

    public void paint(final @NotNull Graphics2D g2, final @NotNull Painter painter, final int height, final int pageOffset) {
      painter.paint(this, g2, height, pageOffset);
    }

    public @Nullable @NlsContexts.Tooltip String getTooltipText() {
      final BreadcrumbsItem element = getItem();
      if (element != null) {
        return element.getTooltip();
      }

      return null;
    }

    public T getItem() {
      return myItem;
    }

    public void performAction(final int modifiers) {
      myLine.setSelectedCrumb(this, modifiers);
    }

    public void setHovered(final boolean b) {
      myHovered = b;
    }
  }

  private static final class NavigationCrumb extends Crumb {
    private static final @NonNls String FORWARD = ">>";
    private static final @NonNls String BACKWARD = "<<";
    private final boolean myForward;
    private final BreadcrumbsComponent myLine;

    NavigationCrumb(final @NotNull BreadcrumbsComponent line,
                    final @NotNull FontMetrics fm,
                    final boolean forward,
                    final @NotNull Painter p) {
      super(forward ? FORWARD : BACKWARD, p.getSize(forward ? FORWARD : BACKWARD, fm, Integer.MAX_VALUE).width + EXTRA_WIDTH);
      myForward = forward;
      myLine = line;
    }

    @Override
    public void performAction(final int modifiers) {
      if (myForward) {
        myLine.nextPage();
      }
      else {
        myLine.previousPage();
      }
    }
  }

  private static final class DummyCrumb extends Crumb {
    DummyCrumb(final int width) {
      super(null, width);
    }

    @Override
    public void paint(final @NotNull Graphics2D g2, final @NotNull Painter painter, final int height, final int pageOffset) {
      // does nothing
    }

    @Override
    public void performAction(final int modifiers) {
      // does nothing
    }

    @Override
    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "DUMMY";
    }
  }

  abstract static class PainterSettings {
    @Nullable
    Color getBackgroundColor(final @NotNull Crumb c) {
      return getAttributes(c).getBackgroundColor();
    }

    @Nullable
    Color getForegroundColor(final @NotNull Crumb c) {
      return getAttributes(c).getForegroundColor();
    }

    @Nullable
    Color getBorderColor(final @NotNull Crumb c) {
      return getAttributes(c).getEffectColor();
    }

    @Nullable
    Font getFont(final @NotNull Graphics g2, final @NotNull Crumb c) {
      return null;
    }

    static @NotNull TextAttributesKey getKey(Crumb c) {
      return
        c.isHovered()
        ? EditorColors.BREADCRUMBS_HOVERED
        : c.isSelected()
          ? EditorColors.BREADCRUMBS_CURRENT
          : c.isLight() && !(c instanceof NavigationCrumb)
            ? EditorColors.BREADCRUMBS_INACTIVE
            : EditorColors.BREADCRUMBS_DEFAULT;
    }
    
    static @NotNull TextAttributes getAttributes(Crumb c) {
      return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(getKey(c));
    }
  }

  static final class ButtonSettings extends PainterSettings {
    public static Color getBackgroundColor(boolean selected, boolean hovered, boolean light, boolean navigationCrumb) {
      return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(
        hovered
        ? EditorColors.BREADCRUMBS_HOVERED
        : selected
          ? EditorColors.BREADCRUMBS_CURRENT
          : light && !navigationCrumb
            ? EditorColors.BREADCRUMBS_INACTIVE
            : EditorColors.BREADCRUMBS_DEFAULT).getBackgroundColor();
    }

    @Override
    @Nullable
    Color getBackgroundColor(final @NotNull Crumb c) {
      final BreadcrumbsItem item = c.getItem();
      if (item != null) {
        final CrumbPresentation presentation = item.getPresentation();
        if (presentation != null) {
          return presentation.getBackgroundColor(c.isSelected(), c.isHovered(), c.isLight());
        }
      }
      return super.getBackgroundColor(c);
    }
  }

  abstract static class Painter {
    public static final int ROUND_VALUE = 2;

    private final PainterSettings mySettings;

    Painter(final @NotNull PainterSettings s) {
      mySettings = s;
    }

    protected PainterSettings getSettings() {
      return mySettings;
    }

    abstract void paint(final @NotNull Crumb c, final @NotNull Graphics2D g2, final int height, final int pageOffset);

    @NotNull
    Dimension getSize(final @NotNull @NonNls String s, final @NotNull FontMetrics fm, final int maxWidth) {
      final int w = fm.stringWidth(s);
      return new Dimension(Math.min(w, maxWidth), fm.getHeight());
    }

  }

  private static final class DefaultPainter extends Painter {
    DefaultPainter(final @NotNull PainterSettings s) {
      super(s);
    }

    @Override
    public void paint(final @NotNull Crumb c, final @NotNull Graphics2D g2, final int height, final int pageOffset) {
      final PainterSettings s = getSettings();
      final Font oldFont = g2.getFont();
      final int offset = c.getOffset() - pageOffset;

      final int width = c.getWidth();
      RectanglePainter.paint(g2, offset + 2, 2, width - 4, height - 4, ROUND_VALUE + 2, s.getBackgroundColor(c), s.getBorderColor(c));

      final Color textColor = s.getForegroundColor(c);
      if (textColor != null) {
        g2.setColor(textColor);
      }

      final Font font = s.getFont(g2, c);
      if (font != null) {
        g2.setFont(font);
      }

      final FontMetrics fm = g2.getFontMetrics();

      String string = THREE_DOTS_AT_LEFT.trim(c.getString(), fm, width);

      g2.drawString(string, offset + ROUND_VALUE + 5, height - fm.getDescent() - 5);

      g2.setFont(oldFont);
    }

    @Override
    @NotNull
    Dimension getSize(final @NotNull @NonNls String s, final @NotNull FontMetrics fm, final int maxWidth) {
      final int width = fm.stringWidth(s) + ROUND_VALUE * 2;
      return new Dimension(Math.min(width, maxWidth), fm.getHeight() + 4);
    }
  }
}
