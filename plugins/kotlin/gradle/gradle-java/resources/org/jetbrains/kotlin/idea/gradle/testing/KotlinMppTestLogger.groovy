// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import groovy.xml.MarkupBuilder
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal

class KotlinMppTestLogger {
    static def configureTestEventLogging(def task) {
        task.addTestListener(new TestListener() {
            @Override
            void beforeSuite(TestDescriptor descriptor) {
                logTestEvent("beforeSuite", (TestDescriptorInternal)descriptor, null, null)
            }

            @Override
            void afterSuite(TestDescriptor descriptor, TestResult result) {
                logTestEvent("afterSuite", (TestDescriptorInternal)descriptor, null, result)
            }

            @Override
            void beforeTest(TestDescriptor descriptor) {
                logTestEvent("beforeTest", (TestDescriptorInternal)descriptor, null, null)
            }

            @Override
            void afterTest(TestDescriptor descriptor, TestResult result) {
                logTestEvent("afterTest", (TestDescriptorInternal)descriptor, null, result)
            }
        })

        task.addTestOutputListener(new TestOutputListener() {
            @Override
            void onOutput(TestDescriptor descriptor, TestOutputEvent event) {
                logTestEvent("onOutput", (TestDescriptorInternal)descriptor, event, null)
            }
        })
    }

    static def logTestEvent(testEventType, TestDescriptorInternal testDescriptor, testEvent, testResult) {
        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)
        xml.event(type: testEventType) {
            test(id: testDescriptor.id, parentId: testDescriptor.parent?.id ?: '') {
                if (testDescriptor) {
                    descriptor(
                            name: testDescriptor.name ?: '',
                            displayName: getName(testDescriptor) ?: '',
                            className: testDescriptor.className ?: ''
                    )
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

                        if ('kotlin.AssertionError'.equals(exception?.class?.name) || exception instanceof AssertionError) {
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
        if (!s) return s;
        s.replaceAll("\r\n|\n\r|\n|\r", "<ijLogEol/>\n")
    }

    static def writeLog(s) {
        println String.format("\n<ijLog>%s</ijLog>", wrap(s))
    }

    static def logTestReportLocation(def report) {
        if (!report) return
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
        if (!t) return ''
        StringWriter sw = new StringWriter()
        t.printStackTrace(new PrintWriter(sw))
        sw.toString()
    }

    static def getName(TestDescriptorInternal descriptor) {
        try {
            return descriptor.getDisplayName() // available starting from ver. 4.10.3
        }
        catch (Throwable ignore) {
            return descriptor.getName()
        }
    }
}