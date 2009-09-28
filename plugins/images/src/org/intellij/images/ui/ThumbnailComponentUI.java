/** $Id$ */
/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.ui;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import org.intellij.images.ImagesBundle;
import org.intellij.images.editor.ImageDocument;
import org.jetbrains.annotations.NonNls;

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
    @NonNls
    private static final String DOTS = "...";
    @NonNls
    private static final String THUMBNAIL_COMPONENT_ERROR_STRING = "ThumbnailComponent.errorString";

    private static final Icon BLANK_ICON = IconLoader.getIcon("/org/intellij/images/icons/ThumbnailBlank.png");
    private static final Icon DIRECTORY_ICON = IconLoader.getIcon("/org/intellij/images/icons/ThumbnailDirectory.png");
    private static final Icon ERROR_ICON = Messages.getErrorIcon();

    private static final Color LINE_COLOR = new Color(0x8E, 0xA8, 0xCE);
    private static final Color PNG_COLOR = new Color(0x80, 0x00, 0x80);
    private static final Color GIF_COLOR = new Color(0x00, 0x80, 0x00);
    private static final Color JPG_COLOR = new Color(0x80, 0x80, 0x00);
    private static final Color BMP_COLOR = new Color(0x00, 0x00, 0x80);

    private static final ThumbnailComponentUI ui = new ThumbnailComponentUI();

    static {
        UIManager.getDefaults().put(THUMBNAIL_COMPONENT_ERROR_STRING,
                ImagesBundle.message("thumbnails.component.error.text"));
    }


    public void paint(Graphics g, JComponent c) {
        ThumbnailComponent tc = (ThumbnailComponent) c;
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
        // Paint directory icon
        DIRECTORY_ICON.paintIcon(tc, g, 5, 5);

        int imagesCount = tc.getImagesCount();
        if (imagesCount > 0) {
            final String title = ImagesBundle.message("icons.count", imagesCount);

            Font font = getSmallFont();
            FontMetrics fontMetrics = g.getFontMetrics(font);
            g.setColor(Color.BLACK);
            g.setFont(font);
            g.drawString(title, 5 + (DIRECTORY_ICON.getIconWidth() - fontMetrics.stringWidth(title)) / 2, DIRECTORY_ICON.getIconHeight() / 2 + fontMetrics.getAscent());
        }
    }

    private void paintImageThumbnail(Graphics g, ThumbnailComponent tc) {
        // Paint blank
        BLANK_ICON.paintIcon(tc, g, 5, 5);

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

        int blankHeight = BLANK_ICON.getIconHeight();

        // Paint image info (and reduce height of text from available height)
        blankHeight -= paintImageCaps(g, image);
        // Paint image format (and reduce height of text from available height)
        blankHeight -= paintFormatText(tc, g);

        // Paint image
        paintThumbnail(g, imageComponent, blankHeight);
    }

    private int paintImageCaps(Graphics g, BufferedImage image) {
        String description = ImagesBundle.message("icon.dimensions", image.getWidth(), image.getHeight(), image.getColorModel().getPixelSize());

        Font font = getSmallFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.drawString(description, 8, 7 + fontMetrics.getAscent());

        return fontMetrics.getHeight();
    }

    private int paintFormatText(ThumbnailComponent tc, Graphics g) {
        Font font = getSmallFont().deriveFont(Font.BOLD);
        FontMetrics fontMetrics = g.getFontMetrics(font);

        String format = tc.getFormat().toUpperCase();
        int stringWidth = fontMetrics.stringWidth(format);
        int x = BLANK_ICON.getIconWidth() - stringWidth + 2;
        int y = BLANK_ICON.getIconHeight() - fontMetrics.getHeight() + 4;
        g.setColor(LINE_COLOR);
        g.drawLine(x - 3, y - 1, x + stringWidth + 1, y - 1);
        g.drawLine(x - 4, y, x - 4, y + fontMetrics.getHeight() - 1);
        g.setColor(getFormatColor(format));
        g.setFont(font);
        g.drawString(
                format,
                x,
                y + fontMetrics.getAscent()
        );

        return fontMetrics.getHeight();
    }

    private Color getFormatColor(String format) {
        if ("PNG".equals(format)) {
            return PNG_COLOR;
        } else if ("GIF".equals(format)) {
            return GIF_COLOR;
        } else if ("JPG".equals(format) || "JPEG".equals(format)) {
            return JPG_COLOR;
        } else if ("BMP".equals(format) || "WBMP".equals(format)) {
            return BMP_COLOR;
        }
        return Color.BLACK;
    }

    private void paintThumbnail(Graphics g, ImageComponent imageComponent, int blankHeight) {

        // Zoom image by available size
        int maxWidth = BLANK_ICON.getIconWidth() - 10;
        int maxHeight = blankHeight - 10;

        BufferedImage image = imageComponent.getDocument().getValue();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        if (imageWidth > maxWidth || imageHeight > maxHeight) {
            if (imageWidth > maxWidth) {
                double proportion = (double) maxWidth / (double) imageWidth;
                imageWidth = maxWidth;
                imageHeight = (int) ((double) imageHeight * proportion);
            }
            if (imageHeight > maxHeight) {
                double proportion = (double) maxHeight / (double) imageHeight;
                imageHeight = maxHeight;
                imageWidth = (int) ((double) imageWidth * proportion);
            }
        }

        imageComponent.setCanvasSize(imageWidth, imageHeight);
        Dimension size = imageComponent.getSize();

        int x = 5 + (BLANK_ICON.getIconWidth() - size.width) / 2;
        int y = 5 + (BLANK_ICON.getIconHeight() - size.height) / 2;


        imageComponent.paint(g.create(x, y, size.width, size.height));
    }

    private void paintFileName(Graphics g, ThumbnailComponent tc) {
        Font font = UIUtil.getLabelFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);

        g.setFont(font);
        g.setColor(tc.getForeground());

        String fileName = tc.getFileName();
        String title = fileName;
        while (fontMetrics.stringWidth(title) > BLANK_ICON.getIconWidth() - 8) {
            title = title.substring(0, title.length() - 1);
        }

        if (fileName.equals(title)) {
            // Center
            g.drawString(fileName, 6 + (BLANK_ICON.getIconWidth() - 2 - fontMetrics.stringWidth(title)) / 2, BLANK_ICON.getIconHeight() + 8 + fontMetrics.getAscent());
        } else {
            int dotsWidth = fontMetrics.stringWidth(DOTS);
            while (fontMetrics.stringWidth(title) > BLANK_ICON.getIconWidth() - 8 - dotsWidth) {
                title = title.substring(0, title.length() - 1);
            }
            g.drawString(title + DOTS, 6, BLANK_ICON.getIconHeight() + 8 + fontMetrics.getAscent());
        }
    }

    private void paintFileSize(Graphics g, ThumbnailComponent tc) {
        Font font = getSmallFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.drawString(
                tc.getFileSizeText(),
                8,
                BLANK_ICON.getIconHeight() + 4 - fontMetrics.getHeight() + fontMetrics.getAscent()
        );
    }

    private void paintError(Graphics g, ThumbnailComponent tc) {
        Font font = getSmallFont();
        FontMetrics fontMetrics = g.getFontMetrics(font);

        ERROR_ICON.paintIcon(
                tc,
                g,
                5 + (BLANK_ICON.getIconWidth() - ERROR_ICON.getIconWidth()) / 2,
                5 + (BLANK_ICON.getIconHeight() - ERROR_ICON.getIconHeight()) / 2
        );

        // Error
        String error = getSubmnailComponentErrorString();
        g.setColor(Color.RED);
        g.setFont(font);
        g.drawString(error, 8, 8 + fontMetrics.getAscent());
    }

    private String getSubmnailComponentErrorString() {
        return UIManager.getString(THUMBNAIL_COMPONENT_ERROR_STRING);
    }

    private static Font getSmallFont() {
        Font labelFont = UIUtil.getLabelFont();
        return labelFont.deriveFont(labelFont.getSize2D() - 2.0f);
    }

    public Dimension getPreferredSize(JComponent c) {
        Font labelFont = UIUtil.getLabelFont();
        FontMetrics fontMetrics = c.getFontMetrics(labelFont);
        return new Dimension(
                BLANK_ICON.getIconWidth() + 10,
                BLANK_ICON.getIconHeight() + fontMetrics.getHeight() + 15
        );
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static ComponentUI createUI(JComponent c) {
        return ui;
    }
}

