package org.intellij.images.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author spleaner
 */
public class ImageInfoReader {
  private static final Logger LOG = Logger.getInstance("#org.intellij.images.util.ImageInfoReader");
  private String myFile;

  private ImageInfoReader(@NotNull final String file) {
    myFile = file;
  }

  @Nullable
  public static Info getInfo(@NotNull final String file) {
    return new ImageInfoReader(file).read();
  }

  @Nullable
  private Info read() {
    RandomAccessFile raf;
    try {
      //noinspection HardCodedStringLiteral
      raf = new RandomAccessFile(myFile, "r");
      try {
        final int b1 = raf.read();
        final int b2 = raf.read();

        if (b1 == 0x47 && b2 == 0x49) {
          return readGif(raf);
        }

        if (b1 == 0x89 && b2 == 0x50) {
          return readPng(raf);
        }

        if (b1 == 0xff && b2 == 0xd8) {
          return readJpeg(raf);
        }

        //if (b1 == 0x42 && b2 == 0x4d) {
        //  return readBmp(raf);
        //}
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        try {
          raf.close();
        }
        catch (IOException e) {
          // nothing
        }
      }
    }
    catch (FileNotFoundException e) {
      // nothing
    }

    return null;
  }

  private static Info readGif(RandomAccessFile raf) throws IOException {
    final byte[] GIF_MAGIC_87A = {0x46, 0x38, 0x37, 0x61};
    final byte[] GIF_MAGIC_89A = {0x46, 0x38, 0x39, 0x61};
    byte[] a = new byte[11]; // 4 from the GIF signature + 7 from the global header

    if (raf.read(a) != 11) {
      return null;
    }

    if ((!eq(a, 0, GIF_MAGIC_89A, 0, 4)) && (!eq(a, 0, GIF_MAGIC_87A, 0, 4))) {
      return null;
    }

    final int width = getShortLittleEndian(a, 4);
    final int height = getShortLittleEndian(a, 6);

    int flags = a[8] & 0xff;
    final int bpp = ((flags >> 4) & 0x07) + 1;

    return new Info(width, height, bpp);
  }

  private static Info readBmp(RandomAccessFile raf) throws IOException {
    byte[] a = new byte[44];
    if (raf.read(a) != a.length) {
      return null;
    }

    final int width = getIntLittleEndian(a, 16);
    final int height = getIntLittleEndian(a, 20);

    if (width < 1 || height < 1) {
      return null;
    }

    final int bpp = getShortLittleEndian(a, 26);
    if (bpp != 1 && bpp != 4 && bpp != 8 && bpp != 16 && bpp != 24 & bpp != 32) {
      return null;
    }

    return new Info(width, height, bpp);
  }

  private static Info readJpeg(RandomAccessFile raf) throws IOException {
    byte[] a = new byte[13];
    while (true) {
      if (raf.read(a, 0, 4) != 4) {
        return null;
      }

      int marker = getShortBigEndian(a, 0);
      final int size = getShortBigEndian(a, 2);

      if ((marker & 0xff00) != 0xff00) {
        return null;
      }

      if (marker == 0xffe0) {
        if (size < 14) {
          raf.skipBytes(size - 2);
          continue;
        }

        if (raf.read(a, 0, 12) != 12) {
          return null;
        }

        raf.skipBytes(size - 14);
      }
      else if (marker >= 0xffc0 && marker <= 0xffcf && marker != 0xffc4 && marker != 0xffc8) {
        if (raf.read(a, 0, 6) != 6) {
          return null;
        }

        final int bpp = (a[0] & 0xff) * (a[5] & 0xff);
        final int width = getShortBigEndian(a, 3);
        final int height = getShortBigEndian(a, 1);

        return new Info(width, height, bpp);
      }
      else {
        raf.skipBytes(size - 2);
      }
    }
  }

  private static Info readPng(RandomAccessFile raf) throws IOException {
    final byte[] PNG_MAGIC = {0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    byte[] a = new byte[27];

    if (raf.read(a) != 27) {
      return null;
    }

    if (!eq(a, 0, PNG_MAGIC, 0, 6)) {
      return null;
    }

    final int width = getIntBigEndian(a, 14);
    final int height = getIntBigEndian(a, 18);
    int bpp = a[22] & 0xff;
    int colorType = a[23] & 0xff;
    if (colorType == 2 || colorType == 6) {
      bpp *= 3;
    }

    return new Info(width, height, bpp);
  }

  private static int getShortBigEndian(byte[] a, int offset) {
    return (a[offset] & 0xff) << 8 | (a[offset + 1] & 0xff);
  }

  private static boolean eq(byte[] a1, int offset1, byte[] a2, int offset2, int num) {
    while (num-- > 0) {
      if (a1[offset1++] != a2[offset2++]) {
        return false;
      }
    }

    return true;
  }

  private static int getIntBigEndian(byte[] a, int offset) {
    return (a[offset] & 0xff) << 24 | (a[offset + 1] & 0xff) << 16 | (a[offset + 2] & 0xff) << 8 | a[offset + 3] & 0xff;
  }

  private static int getIntLittleEndian(byte[] a, int offset) {
    return (a[offset + 3] & 0xff) << 24 | (a[offset + 2] & 0xff) << 16 | (a[offset + 1] & 0xff) << 8 | a[offset] & 0xff;
  }

  private static int getShortLittleEndian(byte[] a, int offset) {
    return (a[offset] & 0xff) | (a[offset + 1] & 0xff) << 8;
  }

  public static class Info {
    public int width;
    public int height;
    public int bpp;

    public Info(int width, int height, int bpp) {
      this.width = width;
      this.height = height;
      this.bpp = bpp;
    }
  }

}
