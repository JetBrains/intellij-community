/** $Id$ */
package org.intellij.images.ui;

import com.intellij.openapi.util.IconLoader;
import org.intellij.images.editor.ImageDocument;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * UI for {@link ThumbnailComponent}.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public class ThumbnailComponentUI extends ComponentUI {
    private static final Icon THUMBNAIL_BLANK = IconLoader.getIcon("/org/intellij/images/icons/ThumbnailBlank.png");
    private static final Icon THUMBNAIL_DIRECTORY = IconLoader.getIcon("/org/intellij/images/icons/ThumbnailDirectory.png");
    private static final Icon THUMBNAIL_ERROR = IconLoader.getIcon("/org/intellij/images/icons/ThumbnailError.png");
    private static final int THUMBNAIL_BLANK_WIDTH = THUMBNAIL_BLANK.getIconWidth();
    private static final int THUMBNAIL_BLANK_HEIGHT = THUMBNAIL_BLANK.getIconHeight();
    private static final String DOTS = "...";

    private static final ThumbnailComponentUI ui = new ThumbnailComponentUI();

    static {
        UIManager.getDefaults().put("ThumbnailComponent.errorString", "Error");
    }


    public void paint(Graphics g, JComponent c) {
        ThumbnailComponent tc = (ThumbnailComponent)c;
        if (tc != null) {
            paintBackground(g, tc);

            if (tc.isDirectory()) {
                paintDirectory(g, tc);
            } else {
                paintImageThumbnail(g, tc);
            }

            // File name
            paintFileName(g, tc);
        }
    }

    private void paintDirectory(Graphics g, ThumbnailComponent tc) {
        THUMBNAIL_DIRECTORY.paintIcon(tc, g, 5, 5);
    }

    private void paintImageThumbnail(Graphics g, ThumbnailComponent tc) {
        ImageComponent imageComponent = tc.getImageComponent();
        ImageDocument document = imageComponent.getDocument();
        BufferedImage image = document.getValue();
        if (image != null) {
            paintImage(g, tc);
        } else {
            paintError(g, tc);
        }
        paintFileSize(g, tc);
    }

    private void paintBackground(Graphics g, ThumbnailComponent tc) {
        Dimension size = tc.getSize();
        g.setColor(tc.getBackground());
        g.fillRect(0, 0, size.width, size.height);
    }

    private void paintImage(Graphics g, ThumbnailComponent tc) {
        ImageComponent imageComponent = tc.getImageComponent();
        BufferedImage image = imageComponent.getDocument().getValue();

        // Paint image blank
        THUMBNAIL_BLANK.paintIcon(tc, g, 5, 5);

        int blankHeight = THUMBNAIL_BLANK_HEIGHT;

        // Paint image info (and reduce height of text from available height)
        blankHeight -= paintImageCaps(g, image);
        // Paint image format (and reduce height of text from available height)
        blankHeight -= paintFormatText(tc, g);

        // Paint image
        paintThumbnail(g, imageComponent, blankHeight);
    }

    private int paintImageCaps(Graphics g, BufferedImage image) {
        String description = image.getWidth() + "x" + image.getHeight() + "x" + image.getColorModel().getPixelSize();

        Font font = getSmallFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.drawString(description, 8, 8 + fontMetrics.getAscent());

        return fontMetrics.getHeight();
    }

    private int paintFormatText(ThumbnailComponent tc, Graphics g) {
        Font font = getSmallFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);

        String format = tc.getFormat();
        int stringWidth = fontMetrics.stringWidth(format);
        g.drawString(format, THUMBNAIL_BLANK_WIDTH - stringWidth - 3, THUMBNAIL_BLANK_HEIGHT + 2 - fontMetrics.getHeight() + fontMetrics.getAscent());

        return fontMetrics.getHeight();
    }

    private void paintThumbnail(Graphics g, ImageComponent imageComponent, int blankHeight) {

        // Zoom image by available size
        int maxWidth = THUMBNAIL_BLANK_WIDTH - 10;
        int maxHeight = blankHeight - 10;

        BufferedImage image = imageComponent.getDocument().getValue();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        double proportion = (double)imageWidth / (double)imageHeight;

        if (imageWidth > maxWidth || imageHeight > maxHeight) {
            if (imageWidth > imageHeight) {
                if (imageWidth > maxWidth) {
                    imageWidth = maxWidth;
                    imageHeight = (int)(maxWidth / proportion);
                }
            } else {
                if (imageHeight > maxHeight) {
                    imageHeight = maxHeight;
                    imageWidth = (int)(maxHeight * proportion);
                }
            }
        }

        imageComponent.setCanvasSize(imageWidth, imageHeight);
        Dimension size = imageComponent.getSize();

        int x = 5 + (THUMBNAIL_BLANK_WIDTH - size.width) / 2;
        int y = 5 + (THUMBNAIL_BLANK_HEIGHT - size.height) / 2;


        imageComponent.paint(g.create(x, y, size.width, size.height));
    }

    private void paintFileName(Graphics g, ThumbnailComponent tc) {
        Font font = getLabelFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);

        g.setFont(font);
        g.setColor(tc.getForeground());

        String fileName = tc.getFileName();
        String title = fileName;
        while (fontMetrics.stringWidth(title) > THUMBNAIL_BLANK_WIDTH - 8) {
            title = title.substring(0, title.length() - 1);
        }

        if (fileName.equals(title)) {
            g.drawString(fileName, 8, THUMBNAIL_BLANK_HEIGHT + 8 + fontMetrics.getAscent());
        } else {
            int dotsWidth = fontMetrics.stringWidth(DOTS);
            while (fontMetrics.stringWidth(title) > THUMBNAIL_BLANK_WIDTH - 8 - dotsWidth) {
                title = title.substring(0, title.length() - 1);
            }
            g.drawString(title + DOTS, 6, THUMBNAIL_BLANK_HEIGHT + 8 + fontMetrics.getAscent());
        }
    }

    private void paintFileSize(Graphics g, ThumbnailComponent tc) {
        Font font = getSmallFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.drawString(tc.getFileSizeText(), 8, THUMBNAIL_BLANK_HEIGHT + 2 - fontMetrics.getHeight() + fontMetrics.getAscent());
    }

    private void paintError(Graphics g, ThumbnailComponent tc) {
        Font font = getSmallFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);
        THUMBNAIL_ERROR.paintIcon(tc, g, 5, 5);
        // Error
        String error = UIManager.getString("ThumbnailComponent.errorString");
        g.setColor(Color.RED);
        g.setFont(font);
        g.drawString(error, 8, 8 + fontMetrics.getAscent());
    }

    private static Font getLabelFont() {
        return UIManager.getFont("Label.font");
    }

    private static Font getSmallFont() {
        Font labelFont = getLabelFont();
        return labelFont.deriveFont(labelFont.getSize2D() - 2.0f);
    }

    public Dimension getPreferredSize(JComponent c) {
        Font labelFont = getLabelFont();
        FontMetrics fontMetrics = c.getFontMetrics(labelFont);
        return new Dimension(THUMBNAIL_BLANK_WIDTH + 10, THUMBNAIL_BLANK_HEIGHT + fontMetrics.getHeight() + 15);
    }

    public static ComponentUI createUI(JComponent c) {
        return ui;
    }
}

