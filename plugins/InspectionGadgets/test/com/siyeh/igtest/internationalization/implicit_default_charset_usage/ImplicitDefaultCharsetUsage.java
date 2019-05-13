package com.siyeh.igtest.internationalization.implicit_default_charset_usage;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Locale;
import java.util.Scanner;

class ImplicitDefaultCharsetUsage {

  void f() throws IOException {
    final byte[] bytes = "asdf".<warning descr="Call to 'getBytes()' uses the platform's default charset">getBytes</warning>();
    "asdf".getBytes("");
    new String();
    new String("asdfas");
    new String(new byte[10], "asdf");
    new <warning descr="'new String()' call uses the platform's default charset">String</warning>(new byte[10]);
    new <warning descr="'new String()' call uses the platform's default charset">String</warning>(new byte[10], 1, 9);
    new <warning descr="'new InputStreamReader()' call uses the platform's default charset">InputStreamReader</warning>(null);
    new InputStreamReader(null, "utf-8");
    new <warning descr="'new OutputStreamWriter()' call uses the platform's default charset">OutputStreamWriter</warning>(null);
    new OutputStreamWriter(null, "utf-8");
    new <warning descr="'new FileReader()' call uses the platform's default charset">FileReader</warning>("asdf");
    new <warning descr="'new FileWriter()' call uses the platform's default charset">FileWriter</warning>((String)null);
    new <warning descr="'new PrintStream()' call uses the platform's default charset">PrintStream</warning>((OutputStream)null);
    new PrintStream("filename", "utf-8");
    new PrintStream("filename");
    new PrintWriter((Writer)null);
    new PrintWriter("filename", "utf-8");
    new <warning descr="'new PrintWriter()' call uses the platform's default charset">PrintWriter</warning>("filename");
    new <warning descr="'new Formatter()' call uses the platform's default charset">Formatter</warning>(new FileOutputStream("null"));
    new Formatter(new FileOutputStream("null"), "utf-8");
    new Formatter(new FileOutputStream("null"), "utf-8", Locale.getDefault());
    new Formatter(System.out);
    new <warning descr="'new Scanner()' call uses the platform's default charset">Scanner</warning>(new FileInputStream("null"));
    new Scanner(new FileInputStream("null"), "utf-8");
    new Scanner("string input");
    new ArrayList(10);
  }

  void charsetEnAndDecoders(InputStream inputStream, OutputStream outputStream) throws IOException {
    final Charset cs = Charset.forName("UTF-8");
    CharsetDecoder cd = cs.newDecoder();
    InputStreamReader is = new InputStreamReader(inputStream, cd);
    CharsetEncoder ce = cs.newEncoder();
    final OutputStreamWriter ow = new OutputStreamWriter(outputStream, ce);
  }
}