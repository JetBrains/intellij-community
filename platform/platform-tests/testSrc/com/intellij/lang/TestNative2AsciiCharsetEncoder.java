// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.vfs.CharsetToolkit;

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * @author zhangxinkun
 */
public class TestNative2AsciiCharsetEncoder {

  /**
   * need to depend boot module
   *
   * @param args
   */
  public static void main(String[] args) {
    Charset charset =null;
    //charset = (Native2AsciiCharset)Native2AsciiCharset.wrap(Charset.forName("utf-8"));
    String s = "#中文\n" +
               "sdgdsg=etregrere的风格大方会让大哥 \n" +
               "sdgdg中=dsgdg\n" +
               "#是的个地方规范  东方宾馆的方便  东方化工的风格#\n" +
               "sdgfdg=dfgf#dg水电费多少 \n" +
               "#中问\n" +
               "#sss中改为\n" +
               "sdg啥fdg=dfgf#dg水电费多少 \n" +
               "sdgf算啦dd#d啦g=dfgf#dg水电费多少 \n" +
               "sds啦s#egfd啦g=dfgf#dg水电费多少 \n";
    byte[] bytes = s.getBytes(charset);
    System.out.println(Arrays.toString(bytes));
    System.out.println(new String(bytes, CharsetToolkit.UTF8_CHARSET));
  }
}
