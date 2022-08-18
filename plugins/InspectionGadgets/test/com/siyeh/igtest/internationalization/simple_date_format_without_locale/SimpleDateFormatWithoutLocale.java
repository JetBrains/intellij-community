package com.siyeh.igtest.internationalization.simple_date_format_without_locale;

import java.text.SimpleDateFormat;
import java.util.Locale;

class SimpleDateFormatWithoutLocale {

  void m() {
    new <warning descr="Instantiating a 'SimpleDateFormat' without specifying a Locale in an internationalized context">SimpleDateFormat</warning>("yyyy");
    new SimpleDateFormat("yyyy", Locale.getDefault());
    java.time.format.DateTimeFormatter.<warning descr="Calling 'DateTimeFormatter.ofPattern()' without specifying a Locale in an internationalized context">ofPattern</warning>("yyyy");
    java.time.format.DateTimeFormatter.ofPattern("yyyy", Locale.getDefault());
  }
}