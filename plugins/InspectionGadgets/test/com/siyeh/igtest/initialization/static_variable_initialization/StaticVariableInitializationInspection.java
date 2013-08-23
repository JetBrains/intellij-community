package com.siyeh.igtest.initialization.static_variable_initialization;

import org.xml.sax.SAXException;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

public class StaticVariableInitializationInspection
{
    public static int s_fooBar;        // may not be initialized
    public static int s_fooBaz = 1;
    public static int s_fooBarangus;
    public static int s_fooBazongas;

    static
    {
        s_fooBarangus = 2;
        staticCall();
    }

    private static void staticCall()
    {
        s_fooBazongas = 3;
    }

    private static final SAXParser SAX_PARSER;

    static {
        try {
            SAX_PARSER = SAXParserFactory.newInstance().newSAXParser();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }
}
class FinalField {
  public static final Object o;

  static {
    System.out.println("o = " + o);
    o = null;
  }
}
