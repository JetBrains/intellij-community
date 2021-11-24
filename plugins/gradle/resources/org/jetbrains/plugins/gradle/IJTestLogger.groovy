// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


import groovy.xml.MarkupBuilder
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult

class IJTestEventLogger {
  static def configureTestEventLogging(def task) {
    task.addTestListener(
      new TestListener() {
        @Override
        void beforeSuite(TestDescriptor descriptor) {
          logTestEvent("beforeSuite", descriptor, null, null)
        }

        @Override
        void afterSuite(TestDescriptor descriptor, TestResult result) {
          logTestEvent("afterSuite", descriptor, null, result)
        }

        @Override
        void beforeTest(TestDescriptor descriptor) {
          logTestEvent("beforeTest", descriptor, null, null)
        }

        @Override
        void afterTest(TestDescriptor descriptor, TestResult result) {
          logTestEvent("afterTest", descriptor, null, result)
        }
      }
    )

    task.addTestOutputListener(new TestOutputListener() {
      @Override
      void onOutput(TestDescriptor descriptor, TestOutputEvent event) {
        logTestEvent("onOutput", descriptor, event, null)
      }
    })
  }

  static def logTestEvent(testEventType, TestDescriptor testDescriptor, testEvent, testResult) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.event(type: testEventType) {
      test(id: testDescriptor.id, parentId: testDescriptor.parent?.id ?: '') {
        if (testDescriptor) {
          descriptor(name: testDescriptor.name ?: '', displayName: getName(testDescriptor) ?: '', className: testDescriptor.className ?: '')
        }
        if (testEvent) {
          def message = escapeCdata(testEvent.message)
          event(destination: testEvent.destination) {
            xml.mkp.yieldUnescaped("$message")
          }
        }
        if (testResult) {
          def errorMsg = escapeCdata(testResult.exception?.message ?: '')
          def stackTrace = escapeCdata(getStackTrace(testResult.exception))
          result(resultType: testResult.resultType ?: '', startTime: testResult.startTime, endTime: testResult.endTime) {
            def exception = testResult.exception
            if (exception?.message?.trim()) xml.mkp.yieldUnescaped("<errorMsg>$errorMsg</errorMsg>")
            if (exception) xml.mkp.yieldUnescaped("<stackTrace>$stackTrace</stackTrace>")

            if ('junit.framework.ComparisonFailure'.equals(exception?.class?.name) ||
                'org.junit.ComparisonFailure'.equals(exception?.class?.name)) {
              def expected = escapeCdata(exception.fExpected)
              def actual = escapeCdata(exception.fActual)
              failureType('comparison')
              xml.mkp.yieldUnescaped("<expected>$expected</expected>")
              xml.mkp.yieldUnescaped("<actual>$actual</actual>")
              return
            }
            try {
              def fileComparisonFailure
              if ('com.intellij.rt.execution.junit.FileComparisonFailure'.equals(exception?.class?.name)) {
                fileComparisonFailure = exception
              }
              else if ('com.intellij.rt.execution.junit.FileComparisonFailure'.equals(exception?.cause?.class?.name)) {
                fileComparisonFailure = exception.cause
              }

              if (fileComparisonFailure) {
                def expected = escapeCdata(fileComparisonFailure.expected)
                def actual = escapeCdata(fileComparisonFailure.actual)
                def filePath = escapeCdata(fileComparisonFailure.filePath)
                def actualFilePath
                if (exception.hasProperty('actualFilePath')) {
                  actualFilePath = escapeCdata(fileComparisonFailure.actualFilePath)
                }
                failureType('comparison')
                xml.mkp.yieldUnescaped("<expected>$expected</expected>")
                xml.mkp.yieldUnescaped("<actual>$actual</actual>")
                xml.mkp.yieldUnescaped("<filePath>$filePath</filePath>")
                if (actualFilePath) xml.mkp.yieldUnescaped("<actualFilePath>$actualFilePath</actualFilePath>")
                return
              }
            }
            catch (ignore) {
            }
            if ('junit.framework.AssertionFailedError'.equals(exception?.class?.name) || exception instanceof AssertionError) {
              failureType('assertionFailed')
              return
            }
            failureType('error')
          }
        }
      }
    }

    writeLog(writer.toString())
  }

  static String escapeCdata(String s) {
    return "<![CDATA[" + s?.getBytes("UTF-8")?.encodeBase64()?.toString() + "]]>";
  }

  static def wrap(String s) {
    if(!s) return s;
    s.replaceAll("\r\n|\n\r|\n|\r","<ijLogEol/>")
  }

  static def writeLog(s) {
    println String.format("<ijLog>%s</ijLog>", wrap(s))
  }

  static def logTestReportLocation(def report) {
    if(!report) return
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.event(type: 'reportLocation', testReport: report)
    writeLog(writer.toString());
  }

  static def logConfigurationError(aTitle, aMessage, boolean openSettings) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.event(type: 'configurationError', openSettings: openSettings) {
      title(aTitle)
      message(aMessage)
    }
    writeLog(writer.toString());
  }

  static def getStackTrace(Throwable t) {
    if(!t) return ''
    StringWriter sw = new StringWriter()
    t.printStackTrace(new PrintWriter(sw))
    sw.toString()
  }

  static def getName(TestDescriptor descriptor) {
    try {
      return descriptor.getDisplayName() // available starting from ver. 4.10.3
    } catch (Throwable ignore) {
      return descriptor.getName()
    }
  }
}