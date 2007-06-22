package org.jetbrains.idea.eclipse.direct;

import com.intellij.openapi.vfs.CharsetToolkit;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.eclipse.util.JDOM;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class JDOMDirect implements JDOM.Impl {

  @NonNls private static final String ENCODING = CharsetToolkit.UTF8;

    private static final String lineSeparator = "\n";

    @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
    public JDOMDirect() {
        JDOM.defaultImpl = this;
    }

    public Document read(InputStream is) throws IOException, JDOMException {
        return new SAXBuilder().build(is);
    }

    public void write(Document document, OutputStream os) throws IOException {
      OutputStreamWriter out = new OutputStreamWriter(os, ENCODING);
      createOutputter().output(document, out);
      out.close();
    }

    private static XMLOutputter createOutputter() {
        XMLOutputter xmlOutputter = new XMLOutputter();
        Format format = Format.getCompactFormat().
                setIndent("    ").
                setTextMode(Format.TextMode.NORMALIZE).
                setEncoding(ENCODING).
                setOmitEncoding(false).
                setOmitDeclaration(false).
                setLineSeparator(lineSeparator);
        xmlOutputter.setFormat(format);
        return xmlOutputter;
    }
}
