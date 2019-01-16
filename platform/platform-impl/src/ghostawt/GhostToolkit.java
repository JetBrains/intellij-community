// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.Dialog.ModalExclusionType;
import java.awt.Dialog.ModalityType;
import java.awt.datatransfer.Clipboard;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.dnd.peer.DragSourceContextPeer;
import java.awt.font.TextAttribute;
import java.awt.im.InputMethodHighlight;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.peer.*;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

/**
 * This is an AWT toolkit implementation which allows to run JDownloader without any graphical user interface. It does not throw a
 * {@link HeadlessException} like other headless implementations, it allows creation of all AWT components but does not actually do any
 * visuals.
 */
public class GhostToolkit extends Toolkit implements sun.awt.KeyboardFocusManagerPeerProvider {
    private static final sun.misc.SoftCache imgCache = new sun.misc.SoftCache();

    private GClipboard clipboard;

    @Override
    protected DesktopPeer createDesktopPeer(Desktop target) throws HeadlessException {
        return new GDesktopPeer();
    }

    @Override
    protected ButtonPeer createButton(Button target) throws HeadlessException {
        return new GButtonPeer(target);
    }

    @Override
    public TextFieldPeer createTextField(TextField target) {
        return new GTextFieldPeer(target);
    }

    @Override
    protected LabelPeer createLabel(Label target) throws HeadlessException {
        return new GLabelPeer(target);
    }

    @Override
    protected ListPeer createList(List target) throws HeadlessException {
        return new GListPeer(target);
    }

    @Override
    protected CheckboxPeer createCheckbox(Checkbox target) throws HeadlessException {
        return new GCheckboxPeer(target);
    }

    @Override
    protected ScrollbarPeer createScrollbar(Scrollbar target) throws HeadlessException {
        return new GScrollbarPeer(target);
    }

    @Override
    protected ScrollPanePeer createScrollPane(ScrollPane target) throws HeadlessException {
        return new GScrollPanePeer(target);
    }

    @Override
    protected TextAreaPeer createTextArea(TextArea target) throws HeadlessException {
        return new GTextAreaPeer(target);
    }

    @Override
    protected ChoicePeer createChoice(Choice target) throws HeadlessException {
        return new GChoicePeer(target);
    }

    @Override
    protected FramePeer createFrame(Frame target) throws HeadlessException {
        return new GFramePeer(target);
    }

    @Override
    protected CanvasPeer createCanvas(Canvas target) {
        return new GCanvasPeer(target);
    }

    @Override
    protected PanelPeer createPanel(Panel target) {
        return new GPanelPeer(target);
    }

    @Override
    protected WindowPeer createWindow(Window target) throws HeadlessException {
        return new GWindowPeer(target);
    }

    @Override
    protected DialogPeer createDialog(Dialog target) throws HeadlessException {
        return new GDialogPeer(target);
    }

    @Override
    protected MenuBarPeer createMenuBar(MenuBar target) throws HeadlessException {
        return new GMenuBarPeer(target);
    }

    @Override
    protected MenuPeer createMenu(Menu target) throws HeadlessException {
        return new GMenuPeer(target);
    }

    @Override
    protected PopupMenuPeer createPopupMenu(PopupMenu target) throws HeadlessException {
        return new GPopupMenuPeer(target);
    }

    @Override
    protected MenuItemPeer createMenuItem(MenuItem target) throws HeadlessException {
        return new GMenuItemPeer(target);
    }

    @Override
    protected FileDialogPeer createFileDialog(FileDialog target) throws HeadlessException {
        return new GFileDialogPeer(target);
    }

    @Override
    protected CheckboxMenuItemPeer createCheckboxMenuItem(CheckboxMenuItem target) throws HeadlessException {
        return new GCheckboxMenuItemPeer(target);
    }

    @Override
    protected FontPeer getFontPeer(String name, int style) {
        return null;
    }

    @Override
    public Dimension getScreenSize() throws HeadlessException {
        return new Dimension(1024, 768);
    }

    @Override
    public int getScreenResolution() throws HeadlessException {
        return 96;
    }

    @Override
    public ColorModel getColorModel() throws HeadlessException {
        return ColorModel.getRGBdefault();
    }

    @Override
    public String[] getFontList() {
        String[] hardwiredFontList = { Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED, Font.DIALOG_INPUT };
        return hardwiredFontList;
    }

    @Override
    public FontMetrics getFontMetrics(Font font) {
        return sun.font.FontDesignMetrics.getMetrics(font);
    }

    @Override
    public void sync() {
        // Nothing to do here.
    }

    @Override
    public Image getImage(String filename) {
        return getImageFromHash(this, filename);
    }

    @Override
    public Image getImage(URL url) {
        return getImageFromHash(this, url);
    }

    private static Image getImageFromHash(Toolkit tk, String filename) {
        synchronized (imgCache) {
            Image img = (Image) imgCache.get(filename);
            if (img == null) {
                try {
                    img = tk.createImage(new sun.awt.image.FileImageSource(filename));
                    imgCache.put(filename, img);
                } catch (Exception e) {
                }
            }
            return img;
        }
    }

    private static Image getImageFromHash(Toolkit tk, URL url) {
        synchronized (imgCache) {
            Image img = (Image) imgCache.get(url);
            if (img == null) {
                try {
                    img = tk.createImage(new sun.awt.image.URLImageSource(url));
                    imgCache.put(url, img);
                } catch (Exception e) {
                }
            }
            return img;
        }
    }

    @Override
    public Image createImage(String filename) {
        return createImage(new sun.awt.image.FileImageSource(filename));
    }

    @Override
    public Image createImage(URL url) {
        return createImage(new sun.awt.image.URLImageSource(url));
    }

    @Override
    public boolean prepareImage(Image img, int w, int h, ImageObserver o) {
        if (w == 0 || h == 0) { return true; }

        if (!(img instanceof sun.awt.image.ToolkitImage)) { return true; }

        sun.awt.image.ToolkitImage tkimg = (sun.awt.image.ToolkitImage) img;
        if (tkimg.hasError()) {
            if (o != null) {
                o.imageUpdate(img, ImageObserver.ERROR | ImageObserver.ABORT, -1, -1, -1, -1);
            }
            return false;
        }
        return tkimg.getImageRep().prepare(o);
    }

    @Override
    public int checkImage(Image img, int w, int h, ImageObserver o) {
        if (!(img instanceof sun.awt.image.ToolkitImage)) { return ImageObserver.ALLBITS; }

        sun.awt.image.ToolkitImage tkimg = (sun.awt.image.ToolkitImage) img;
        int repbits;
        if (w == 0 || h == 0) {
            repbits = ImageObserver.ALLBITS;
        } else {
            repbits = tkimg.getImageRep().check(o);
        }
        return tkimg.check(o) | repbits;
    }

    @Override
    public Image createImage(ImageProducer producer) {
        return new sun.awt.image.ToolkitImage(producer);
    }

    @Override
    public Image createImage(byte[] data, int offset, int length) {
        return createImage(new sun.awt.image.ByteArrayImageSource(data, offset, length));
    }

    @Override
    public PrintJob getPrintJob(Frame frame, String jobtitle, Properties props) {
        return null;
    }

    @Override
    public void beep() {
        System.out.write(0x07);
    }

    @Override
    public Clipboard getSystemClipboard() throws HeadlessException {
        synchronized (this) {
            if (clipboard == null) {
                clipboard = new GClipboard();
            }
        }
        return clipboard;
    }

    @Override
    protected EventQueue getSystemEventQueueImpl() {
        return getSystemEventQueueImplPP();
    }

    static EventQueue getSystemEventQueueImplPP() {
        return getSystemEventQueueImplPP(sun.awt.AppContext.getAppContext());
    }

    public static EventQueue getSystemEventQueueImplPP(sun.awt.AppContext appContext) {
        EventQueue theEventQueue = (EventQueue) appContext.get(sun.awt.AppContext.EVENT_QUEUE_KEY);
        return theEventQueue;
    }

    @Override
    public DragSourceContextPeer createDragSourceContextPeer(DragGestureEvent dge) throws InvalidDnDOperationException {
        // throwing this exception is ok, it just indicates that
        // drag and drop is not supported
        throw new InvalidDnDOperationException("Headless environment");
    }

    @Override
    public boolean isModalityTypeSupported(ModalityType modalityType) {
        return false;
    }

    @Override
    public boolean isModalExclusionTypeSupported(ModalExclusionType modalExclusionType) {
        return false;
    }

    @Override
    public Map<TextAttribute, ?> mapInputMethodHighlight(InputMethodHighlight highlight) throws HeadlessException {
        return GInputMethod.mapInputMethodHighlight(highlight);
    }

    @Override
    public java.awt.peer.KeyboardFocusManagerPeer getKeyboardFocusManagerPeer() {
        return new GKeyboardFocusManagerPeer();
    }

    @Override
    protected MouseInfoPeer getMouseInfoPeer() {
        return new GMouseInfoPeer();
    }
}