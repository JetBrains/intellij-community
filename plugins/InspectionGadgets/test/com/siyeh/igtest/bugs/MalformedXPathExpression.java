package com.siyeh.igtest.bugs;


import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

public class MalformedXPathExpression {

    public void fool() throws XPathExpressionException {
        final XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.evaluate("/foo/bar[0]/@@Foo", null);
    }
}
