// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//file:noinspection GrPackage

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

  static void logTestEvent(String testEventType, TestDescriptor testDescriptor, TestOutputEvent testEvent, TestResult testResult) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.event(type: testEventType) {
      test(id: testDescriptor.id, parentId: testDescriptor.parent?.id ?: '') {
        if (testDescriptor != null) {
          descriptor(name: testDescriptor.name ?: '', displayName: getName(testDescriptor) ?: '', className: testDescriptor.className ?: '')
        }
        if (testEvent != null) {
          def message = escapeCdata(testEvent.message)
          event(destination: testEvent.destination) {
            xml.mkp.yieldUnescaped("$message")
          }
        }
        if (testResult != null) {
          result(resultType: testResult.resultType ?: '', startTime: testResult.startTime, endTime: testResult.endTime) {
            def exception = testResult.exception

            if (exception != null) {
              logFailureDescriptor(xml, exception)
            }

            if ('org.junit.ComparisonFailure' == exception?.class?.name) {
              logJunit4ComparisonFailure(xml, exception)
            }
            else if ('junit.framework.ComparisonFailure' == exception?.class?.name) {
              logJunit4ComparisonFailure(xml, exception)
            }
            else if ('org.opentest4j.AssertionFailedError' == exception?.class?.name) {
              logJunit5ComparisonFailure(xml, exception)
            }
            else if ('com.intellij.rt.execution.junit.FileComparisonFailure' == exception?.class?.name) {
              logIjFileComparisonFailure(xml, exception)
            }
            else if ('com.intellij.rt.execution.junit.FileComparisonFailure' == exception?.cause?.class?.name) {
              logIjFileComparisonFailure(xml, exception.cause)
            }
            else if (exception instanceof AssertionError) {
              xml.failureType('assertionFailed')
            }
            else {
              xml.failureType('error')
            }
          }
        }
      }
    }

    writeLog(writer.toString())
  }

  private static def logFailureDescriptor(MarkupBuilder xml, Throwable exception) {
    def errorMsg = escapeCdata(exception.message)
    def stackTrace = escapeCdata(getStackTrace(exception))

    xml.mkp.yieldUnescaped("<errorMsg>$errorMsg</errorMsg>")
    xml.mkp.yieldUnescaped("<stackTrace>$stackTrace</stackTrace>")
  }

  // org.junit.ComparisonFailure | junit.framework.ComparisonFailure
  private static def logJunit4ComparisonFailure(MarkupBuilder xml, Throwable exception) {
    def expected = escapeCdata(exception.fExpected)
    def actual = escapeCdata(exception.fActual)

    xml.failureType('comparison')
    xml.mkp.yieldUnescaped("<expected>$expected</expected>")
    xml.mkp.yieldUnescaped("<actual>$actual</actual>")
  }

  // org.opentest4j.AssertionFailedError
  private static def logJunit5ComparisonFailure(MarkupBuilder xml, Throwable exception) {
    def expected = escapeCdata(exception.expected.stringRepresentation)
    def actual = escapeCdata(exception.actual.stringRepresentation)

    xml.failureType('comparison')
    xml.mkp.yieldUnescaped("<expected>$expected</expected>")
    xml.mkp.yieldUnescaped("<actual>$actual</actual>")
  }

  // com.intellij.rt.execution.junit.FileComparisonFailure
  private static def logIjFileComparisonFailure(MarkupBuilder xml, Throwable exception) {
    def expected = escapeCdata(exception.expected)
    def actual = escapeCdata(exception.actual)
    def filePath = escapeCdata(exception.filePath)
    def actualFilePath = null
    if (exception.hasProperty('actualFilePath')) {
      actualFilePath = escapeCdata(exception.actualFilePath)
    }

    xml.failureType('comparison')
    xml.mkp.yieldUnescaped("<expected>$expected</expected>")
    xml.mkp.yieldUnescaped("<actual>$actual</actual>")
    xml.mkp.yieldUnescaped("<filePath>$filePath</filePath>")
    xml.mkp.yieldUnescaped("<actualFilePath>$actualFilePath</actualFilePath>")
  }

  private static String escapeCdata(String s) {
    if (s == null) {
      return null
    }
    def encodedString = s.getBytes("UTF-8").encodeBase64()
    return "<![CDATA[$encodedString]]>"
  }

  static def wrap(String s) {
    if (!s) return s
    s.replaceAll("\r\n|\n\r|\n|\r", "<ijLogEol/>")
  }

  static def writeLog(s) {
    println String.format("<ijLog>%s</ijLog>", wrap(s))
  }

  static def logTestReportLocation(def report) {
    if(!report) return
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.event(type: 'reportLocation', testReport: report)
    writeLog(writer.toString())
  }

  static def logConfigurationError(aTitle, aMessage, boolean openSettings) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.event(type: 'configurationError', openSettings: openSettings) {
      title(aTitle)
      message(aMessage)
    }
    writeLog(writer.toString())
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