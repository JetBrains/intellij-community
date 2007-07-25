package com.intellij.html.preview;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.css.CssTerm;
import com.intellij.psi.css.impl.CssTermTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.reference.SoftReference;
import com.intellij.util.ui.UIUtil;
import com.intellij.patterns.impl.StandardPatterns;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author spleaner
 */
public class ImagePreviewComponent extends JPanel {
  private static final Key<Long> TIMESTAMP_KEY = Key.create("Image.timeStamp");
  private static final Key<SoftReference<BufferedImage>> BUFFERED_IMAGE_REF_KEY = Key.create("Image.bufferedImage");
  private static final Key<String> FORMAT_KEY = Key.create("Image.format");

  private static final List<String> supportedExtensions = Arrays.asList(ImageIO.getReaderFormatNames());
  private BufferedImage myImage;
  private Dimension myPreferredSize;

  private ImagePreviewComponent(@NotNull final BufferedImage image) {
    myImage = image;

    setBackground(UIUtil.getToolTipBackground());
    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.black), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

    if (image.getWidth() > 300 || image.getHeight() > 300) {
      // will make image smaller
      final float factor = 300.0f / Math.max(image.getWidth(), image.getHeight());
      myPreferredSize = new Dimension((int)(image.getWidth() * factor) + 12, (int)(image.getHeight() * factor) + 12);
    }
    else {
      myPreferredSize = new Dimension(image.getWidth() + 12, image.getHeight() + 12);
    }
  }

  public Dimension getPreferredSize() {
    return myPreferredSize;
  }

  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  public void paint(final Graphics g) {
    super.paint(g);
    Rectangle r = getBounds();
    g.drawImage(myImage, 6, 6, r.width - 12, r.height - 12, this);
  }

  @SuppressWarnings({"AutoUnboxing"})
  private static boolean refresh(@NotNull VirtualFile file) throws IOException {
    Long loadedTimeStamp = file.getUserData(TIMESTAMP_KEY);
    SoftReference<BufferedImage> imageRef = file.getUserData(BUFFERED_IMAGE_REF_KEY);
    if (loadedTimeStamp == null || loadedTimeStamp < file.getTimeStamp() || imageRef == null || imageRef.get() == null) {
      try {
        final byte[] content = file.contentsToByteArray();
        InputStream inputStream = new ByteArrayInputStream(content, 0, content.length);
        ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream);
        try {
          Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
          if (imageReaders.hasNext()) {
            ImageReader imageReader = imageReaders.next();
            try {
              file.putUserData(FORMAT_KEY, imageReader.getFormatName());
              ImageReadParam param = imageReader.getDefaultReadParam();
              imageReader.setInput(imageInputStream, true, true);
              int minIndex = imageReader.getMinIndex();
              BufferedImage image = imageReader.read(minIndex, param);
              file.putUserData(BUFFERED_IMAGE_REF_KEY, new SoftReference<BufferedImage>(image));
              return true;
            }
            finally {
              imageReader.dispose();
            }
          }
        }
        finally {
          imageInputStream.close();
        }
      }
      finally {
        // We perform loading no more needed
        file.putUserData(TIMESTAMP_KEY, System.currentTimeMillis());
      }
    }
    return false;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public static JComponent getPreviewComponent(@NotNull final PsiElement element) {
    if (StandardPatterns.psiElement().withParent(StandardPatterns.psiElement(PsiLiteralExpression.class)).accepts(element)) {
      final PsiLiteralExpression psiLiteralExpression = (PsiLiteralExpression) element.getParent();
      if (psiLiteralExpression != null) {
        return _getPreviewComponent(psiLiteralExpression);
      }
    }

    CssTerm term = PsiTreeUtil.getParentOfType(element, CssTerm.class, false);
    if (term != null && CssTermTypes.URI == term.getTermType()) {
      PsiElement parent = element;
      while (parent != term) {
        final JComponent c = _getPreviewComponent(parent);
        if (c != null) {
          return c;
        }

        parent = parent.getParent();
      }
    }
    else if (element.getParent() instanceof XmlAttributeValue) {
      final PsiElement attributeValue = element.getParent();
      if (attributeValue.getParent() instanceof XmlAttribute) {
        XmlAttribute attribute = (XmlAttribute)attributeValue.getParent();
        String attrName = attribute.getName();
        if ("background".equals(attrName) || "src".equals(attrName) || "href".equals(attrName)) {
          PsiElement parent = element;
          while (parent != attribute) {
            final JComponent c = _getPreviewComponent(parent);
            if (c != null) {
              return c;
            }

            parent = parent.getParent();
          }
        }
      }
    }

    return null;
  }

  private static JComponent _getPreviewComponent(final PsiElement parent) {
    final PsiReference[] references = parent.getReferences();
    for (final PsiReference reference : references) {
      final PsiElement fileItem = reference.resolve();
      if (fileItem instanceof PsiFileSystemItem) {
        final PsiFileSystemItem item = (PsiFileSystemItem) fileItem;
        if (!item.isDirectory()) {
          final VirtualFile file = item.getVirtualFile();
          if (file != null && supportedExtensions.contains(file.getExtension())) {
            try {
              refresh(file);
              SoftReference<BufferedImage> imageRef = file.getUserData(BUFFERED_IMAGE_REF_KEY);
              if (imageRef != null) {
                return new ImagePreviewComponent(imageRef.get());
              }
            }
            catch (IOException e) {
              // nothing
            }
          }
        }
      }
    }

    return null;
  }
}
