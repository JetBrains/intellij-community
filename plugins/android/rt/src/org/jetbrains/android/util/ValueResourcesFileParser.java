package org.jetbrains.android.util;

import com.android.resources.ResourceType;
import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;

/**
* @author Eugene.Kudelevsky
*/
public abstract class ValueResourcesFileParser implements IXMLBuilder {
  private boolean mySeenResources;
  private String myLastTypeAttr;
  private String myLastNameAttr;

  public ValueResourcesFileParser() {
    mySeenResources = false;
    myLastTypeAttr = null;
    myLastNameAttr = null;
  }

  @Override
  public void startBuilding(String systemID, int lineNr) throws Exception {
  }

  @Override
  public void newProcessingInstruction(String target, Reader reader) throws Exception {
  }

  @Override
  public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
    if (!mySeenResources) {
      if ("resources".equals(name)) {
        mySeenResources = true;
      }
      else {
        stop();
      }
    }
    myLastNameAttr = null;
    myLastTypeAttr = null;
  }

  protected abstract void stop();

  protected abstract void process(@NotNull ResourceEntry resourceEntry);

  @Override
  public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
    if ("name".equals(key)) {
      myLastNameAttr = value;
    }
    else if ("type".equals(key)) {
      myLastTypeAttr = value;
    }
  }

  @Override
  public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
    if (myLastNameAttr != null && name != null) {
      final String resType = "item".equals(name)
                             ? myLastTypeAttr
                             : AndroidCommonUtils.getResourceTypeByTagName(name);
      if (resType != null && ResourceType.getEnum(resType) != null) {
        process(new ResourceEntry(resType, myLastNameAttr));
      }
    }
  }

  @Override
  public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
  }

  @Override
  public void addPCData(Reader reader, String systemID, int lineNr) throws Exception {
  }

  @Nullable
  @Override
  public Object getResult() throws Exception {
    return null;
  }
}
