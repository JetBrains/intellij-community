// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.openapi.util.text.Strings;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.xml.XppDriver;
import org.jetbrains.annotations.NotNull;

import java.io.StringReader;

public class TestEventXPPXmlView implements TestEventXmlView {
  private static final HierarchicalStreamDriver DRIVER = new XppDriver();

  private String myTestEventType;
  private String myTestParentId;
  private String myTestId;
  private String myTestClassName;
  private String myTestEventResultType;
  private String myEventTitle;
  private String myEventOpenSettings;
  private String myEventMessage;
  private String myTestEventTest;
  private String myTestEventTestDescription;
  private String myEventTestReport;
  private String myEventTestResultActionFilePath;
  private String myEventTestResultFilePath;
  private String myEventTestResultExpected;
  private String myEventTestResultActual;
  private String myEventTestResultFailureType;
  private String myEventTestResultExceptionName;
  private String myEventTestResultStackTrace;
  private String myEventTestResultErrorMsg;
  private String myEventTestResultEndTime;
  private String myEventTestResultStartTime;
  private String myTestName;
  private String myTestDisplayName;

  public TestEventXPPXmlView(final @NotNull String xml) {
    final HierarchicalStreamReader parser = DRIVER.createReader(new StringReader(xml));

    if (!"ijLog".equals(parser.getNodeName())) throw new RuntimeException("root element must be 'ijLog'");

    while(parser.hasMoreChildren()) {
      parser.moveDown();

      if ("event".equals(parser.getNodeName())) {
        myTestEventType = parser.getAttribute("type");                //queryXml("/ijLog/event/@type");
        myEventOpenSettings = parser.getAttribute("openSettings");    //queryXml("/ijLog/event/@openSettings");
        myEventTestReport = parser.getAttribute("testReport");        //queryXml("/ijLog/event/@testReport");

        while (parser.hasMoreChildren()) {
          parser.moveDown();

          if ("title".equals(parser.getNodeName())) {
            myEventTitle = parser.getValue();                               //queryXml("/ijLog/event/title");
          } else if ("message".equals(parser.getNodeName())) {
            myEventMessage = parser.getValue();                             //queryXml("/ijLog/event/message");
          } else if ("test".equals(parser.getNodeName())) {
            myTestParentId = parser.getAttribute("parentId");        //queryXml("/ijLog/event/test/@parentId");
            myTestId = parser.getAttribute("id");                    //queryXml("/ijLog/event/test/@id");

            while (parser.hasMoreChildren()) {
              parser.moveDown();

              if ("descriptor".equals(parser.getNodeName())) {
                myTestName = parser.getAttribute("name");            //queryXml("/ijLog/event/test/descriptor/@name");
                myTestDisplayName = parser.getAttribute("displayName");            //queryXml("/ijLog/event/test/descriptor/@name");
                myTestClassName = parser.getAttribute("className");  //queryXml("/ijLog/event/test/descriptor/@className");
              } else if ("event".equals(parser.getNodeName())) {
                myTestEventTestDescription = parser.getAttribute("destination"); //queryXml("/ijLog/event/test/event/@destination");
                myTestEventTest = parser.getValue();                                    //queryXml("/ijLog/event/test/event");
              } else if ("result".equals(parser.getNodeName())) {
                myTestEventResultType = parser.getAttribute("resultType");       //queryXml("/ijLog/event/test/result/@resultType");
                myEventTestResultEndTime = parser.getAttribute("endTime");       //queryXml("/ijLog/event/test/result/@endTime");
                myEventTestResultStartTime = parser.getAttribute("startTime");   //queryXml("/ijLog/event/test/result/@startTime");

                while(parser.hasMoreChildren()) {
                  parser.moveDown();

                  if ("actualFilePath".equals(parser.getNodeName())){
                    myEventTestResultActionFilePath = parser.getValue();             //queryXml("/ijLog/event/test/result/actualFilePath");
                  } else if ("filePath".equals(parser.getNodeName())){
                    myEventTestResultFilePath = parser.getValue();                   //queryXml("/ijLog/event/test/result/filePath");
                  } else if ("expected".equals(parser.getNodeName())){
                    myEventTestResultExpected = parser.getValue();                   //queryXml("/ijLog/event/test/result/expected");
                  } else if ("actual".equals(parser.getNodeName())){
                    myEventTestResultActual = parser.getValue();                     //queryXml("/ijLog/event/test/result/actual");
                  } else if ("failureType".equals(parser.getNodeName())){
                    myEventTestResultFailureType = parser.getValue();                //queryXml("/ijLog/event/test/result/failureType");
                  } else if ("exceptionName".equals(parser.getNodeName())){
                    myEventTestResultExceptionName = parser.getValue();              //queryXml("/ijLog/event/test/result/exceptionName");
                  } else if ("stackTrace".equals(parser.getNodeName())){
                    myEventTestResultStackTrace = parser.getValue();                 //queryXml("/ijLog/event/test/result/stackTrace");
                  } else if ("errorMsg".equals(parser.getNodeName())){
                    myEventTestResultErrorMsg = parser.getValue();                   //queryXml("/ijLog/event/test/result/errorMsg");
                  }
                  parser.moveUp();
                }
              }

              parser.moveUp();
            }
          }

          parser.moveUp();
        }
      }

      parser.moveUp();
    }
  }

  @Override
  public @NotNull String getTestEventType() {
    return myTestEventType == null ? "" : myTestEventType;
  }

  @Override
  public @NotNull String getTestName() {
    return myTestName == null ? "" : myTestName;
  }

  @Override
  public @NotNull String getTestDisplayName() {
    return Strings.isEmpty(myTestDisplayName) ? getTestName() : myTestDisplayName;
  }

  @Override
  public @NotNull String getTestParentId() {
    return myTestParentId == null ? "" : myTestParentId;
  }

  @Override
  public @NotNull String getTestId() {
    return myTestId == null ? "" : myTestId;
  }

  @Override
  public @NotNull String getTestClassName() {
    return myTestClassName == null ? "" : myTestClassName;
  }

  @Override
  public @NotNull String getTestEventResultType() {
    return myTestEventResultType == null ? "" : myTestEventResultType;
  }

  @Override
  public @NotNull String getEventTitle() {
    return myEventTitle == null ? "" : myEventTitle;
  }

  @Override
  public boolean isEventOpenSettings() {
    return Boolean.parseBoolean(myEventOpenSettings == null ? "" : myEventOpenSettings);
  }

  @Override
  public @NotNull String getEventMessage() {
    return myEventMessage == null ? "" : myEventMessage;
  }

  @Override
  public @NotNull String getTestEventTest() {
    return myTestEventTest == null ? "" : myTestEventTest;
  }

  @Override
  public @NotNull String getTestEventTestDescription() {
    return myTestEventTestDescription == null ? "" : myTestEventTestDescription;
  }

  @Override
  public @NotNull String getEventTestReport() {
    return myEventTestReport == null ? "" : myEventTestReport;
  }

  @Override
  public @NotNull String getEventTestResultActualFilePath() {
    return myEventTestResultActionFilePath == null ? "" : myEventTestResultActionFilePath;
  }

  @Override
  public @NotNull String getEventTestResultFilePath() {
    return myEventTestResultFilePath == null ? "" : myEventTestResultFilePath;
  }

  @Override
  public @NotNull String getEventTestResultExpected() {
    return myEventTestResultExpected == null ? "" : myEventTestResultExpected;
  }

  @Override
  public @NotNull String getEventTestResultActual() {
    return myEventTestResultActual == null ? "" : myEventTestResultActual;
  }

  @Override
  public @NotNull String getEventTestResultFailureType() {
    return myEventTestResultFailureType == null ? "" : myEventTestResultFailureType;
  }

  @Override
  public @NotNull String getEventTestResultExceptionName() {
    return myEventTestResultExceptionName == null ? "" : myEventTestResultExceptionName;
  }

  @Override
  public @NotNull String getEventTestResultStackTrace() {
    return myEventTestResultStackTrace == null ? "" : myEventTestResultStackTrace;
  }

  @Override
  public @NotNull String getEventTestResultErrorMsg() {
    return myEventTestResultErrorMsg == null ? "" : myEventTestResultErrorMsg;
  }

  @Override
  public @NotNull String getEventTestResultEndTime() {
    return myEventTestResultEndTime == null ? "" : myEventTestResultEndTime;
  }

  @Override
  public @NotNull String getEventTestResultStartTime() {
    return myEventTestResultStartTime == null ? "" : myEventTestResultStartTime;
  }
}
