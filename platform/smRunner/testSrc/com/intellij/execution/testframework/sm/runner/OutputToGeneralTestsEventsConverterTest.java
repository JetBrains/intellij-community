// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.text.StringUtil;

import java.util.Collections;
import java.util.List;

/**
 * @author Roman.Chernyatchik
 */
public class OutputToGeneralTestsEventsConverterTest extends BaseSMTRunnerTestCase {
  private ProcessOutputConsumer myOutputConsumer;
  public MockGeneralTestEventsProcessorAdapter myEnventsProcessor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final String testFrameworkName = "SMRunnerTests";
    final SMTRunnerConsoleProperties properties = new SMTRunnerConsoleProperties(createRunConfiguration(),
                                                                                 testFrameworkName,
                                                                                 DefaultRunExecutor.getRunExecutorInstance()) {
      @Override
      public boolean serviceMessageHasNewLinePrefix() {
        return true;
      }
    };
    myOutputConsumer = new OutputToGeneralTestEventsConverter(testFrameworkName, properties);
    myEnventsProcessor = new MockGeneralTestEventsProcessorAdapter(properties.getProject(), testFrameworkName);
    myOutputConsumer.setProcessor(myEnventsProcessor);
  }

  public void testLineBreaks_ServiceMessage() {
    doCheckOutptut("##teamcity[enteredTheMatrix timestamp = '2011-06-03T13:00:08.259+0400']\n", "", true);
  }

  public void testLineBreaks_NormalOutput() {
    doCheckOutptut("\na\nb\n\nc\n", """
                     [stdout]
                     [stdout]a
                     [stdout]b
                     [stdout]
                     [stdout]c
                     """,
                   true);
  }

  public void testLineBreaks_OutptutAndCommands() {
    doCheckOutptut("\na\n##teamcity[enteredTheMatrix timestamp = '2011-06-03T13:00:08.259+0400']\nb\n##teamcity[enteredTheMatrix timestamp = '2011-06-03T13:00:08.259+0400']\n\nc\n",
                   """
                     [stdout]
                     [stdout]a
                     [stdout]b
                     [stdout]
                     [stdout]c
                     """,
                   true);
  }

  public void testLineBreaks_AutoSplitIfProcessHandlerDoestSupportIt() {
    doCheckOutptut("\na\n##teamcity[enteredTheMatrix timestamp = '2011-06-03T13:00:08.259+0400']\nb\n##teamcity[testCount count = '1' timestamp = '2011-06-03T13:00:08.259+0400']\n\nc\n",
                   """
                     [stdout]
                     [stdout]a
                     [stdout]b
                     [stdout]
                     [stdout]c
                     """,
                   false);
  }

  public void testMergingLineBreaks() {
    doCheckOutptut("""
                     Testing started at 11:14 ...
                     /bin/bash -c "/Users/user/.rvm/bin/rvm ruby-2.5.3@everydayrails do /Users/user/.rvm/rubies/ruby-2.5.3/bin/ruby /Users/user/Downloads/everydayrails-2017/bin/rspec /Users/user/Downloads/everydayrails-2017/spec --require teamcity/spec/runner/formatter/teamcity/formatter --format Spec::Runner::Formatter::TeamcityFormatter --pattern '**/*_spec.rb' --no-color"

                     /Users/user/.rvm/gems/ruby-2.5.3@everydayrails/gems/activesupport-5.1.1/lib/active_support/core_ext/hash/slice.rb:21: warning: method redefined; discarding old slice
                     ##teamcity[enteredTheMatrix timestamp = '2019-07-18T11:14:50.785+0200']
                     /Users/user/.rvm/gems/ruby-2.5.3@everydayrails/gems/activesupport-5.1.1/lib/active_support/core_ext/class/attribute.rb:90: warning: previous definition of default_url_options was here

                     ##teamcity[testCount count = '70' timestamp = '2019-07-18T11:15:00.667+0200']

                     ##teamcity[testSuiteStarted name = 'Task' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/task_spec.rb:3' timestamp = '2019-07-18T11:15:02.498+0200']
                     /Users/user/.rvm/gems/ruby-2.5.3@everydayrails/gems/concurrent-ruby-1.0.5/lib/concurrent/concern/logging.rb:20: warning: instance variable @logger not initialized

                     ##teamcity[testStarted name = 'Task is valid with a project and name' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/task_spec.rb:6' timestamp = '2019-07-18T11:15:02.499+0200']

                     ##teamcity[testFinished name = 'Task is valid with a project and name' duration = '18' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302517, 1563441302499), duration=18, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.017656' timestamp = '2019-07-18T11:15:02.517+0200']
                     /Users/user/.rvm/gems/ruby-2.5.3@everydayrails/gems/concurrent-ruby-1.0.5/lib/concurrent/concern/logging.rb:20: warning: instance variable @logger not initialized

                     ##teamcity[testStarted name = 'Task is invalid without a project' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/task_spec.rb:14' timestamp = '2019-07-18T11:15:02.517+0200']

                     ##teamcity[testFinished name = 'Task is invalid without a project' duration = '4' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302522, 1563441302518), duration=4, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.003445' timestamp = '2019-07-18T11:15:02.522+0200']

                     ##teamcity[testStarted name = 'Task is invalid without a name' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/task_spec.rb:20' timestamp = '2019-07-18T11:15:02.522+0200']

                     ##teamcity[testFinished name = 'Task is invalid without a name' duration = '3' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302525, 1563441302522), duration=3, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.002422' timestamp = '2019-07-18T11:15:02.525+0200']

                     ##teamcity[testSuiteFinished name = 'Task' timestamp = '2019-07-18T11:15:02.525+0200']

                     ##teamcity[testSuiteStarted name = 'User' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/user_spec.rb:3' timestamp = '2019-07-18T11:15:02.525+0200']

                     ##teamcity[testStarted name = 'User has a valid factory' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/user_spec.rb:4' timestamp = '2019-07-18T11:15:02.525+0200']

                     ##teamcity[testFinished name = 'User has a valid factory' duration = '4' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302530, 1563441302526), duration=4, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.004053' timestamp = '2019-07-18T11:15:02.530+0200']

                     ##teamcity[testStarted name = 'User is valid with a first name, last name and email, and password' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/user_spec.rb:8' timestamp = '2019-07-18T11:15:02.530+0200']

                     ##teamcity[testFinished name = 'User is valid with a first name, last name and email, and password' duration = '3' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302533, 1563441302530), duration=3, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.003402' timestamp = '2019-07-18T11:15:02.534+0200']

                     ##teamcity[testStarted name = 'User example at ./spec/models/user_spec.rb:18' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/user_spec.rb:18' timestamp = '2019-07-18T11:15:02.534+0200']

                     ##teamcity[testFinished name = 'User example at ./spec/models/user_spec.rb:18' duration = '5' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302539, 1563441302534), duration=5, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.005233' timestamp = '2019-07-18T11:15:02.539+0200']

                     ##teamcity[testStarted name = 'User example at ./spec/models/user_spec.rb:19' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/user_spec.rb:19' timestamp = '2019-07-18T11:15:02.540+0200']

                     ##teamcity[testFinished name = 'User example at ./spec/models/user_spec.rb:19' duration = '4' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302544, 1563441302540), duration=4, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.004293' timestamp = '2019-07-18T11:15:02.544+0200']

                     ##teamcity[testStarted name = 'User example at ./spec/models/user_spec.rb:20' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/user_spec.rb:20' timestamp = '2019-07-18T11:15:02.545+0200']

                     ##teamcity[testFinished name = 'User example at ./spec/models/user_spec.rb:20' duration = '9' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302554, 1563441302545), duration=9, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.009547' timestamp = '2019-07-18T11:15:02.555+0200']

                     ##teamcity[testStarted name = 'User example at ./spec/models/user_spec.rb:21' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/user_spec.rb:21' timestamp = '2019-07-18T11:15:02.555+0200']

                     ##teamcity[testFinished name = 'User example at ./spec/models/user_spec.rb:21' duration = '32' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302588, 1563441302556), duration=32, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.031928' timestamp = '2019-07-18T11:15:02.588+0200']

                     ##teamcity[testStarted name = 'User returns a user|'s full name as a string' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/user_spec.rb:23' timestamp = '2019-07-18T11:15:02.589+0200']

                     ##teamcity[testFinished name = 'User returns a user|'s full name as a string' duration = '6' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302595, 1563441302589), duration=6, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.005719' timestamp = '2019-07-18T11:15:02.595+0200']

                     ##teamcity[testStarted name = 'User sends a welcome email on account creation' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/user_spec.rb:28' timestamp = '2019-07-18T11:15:02.597+0200']
                     /Users/user/.rvm/gems/ruby-2.5.3@everydayrails/gems/concurrent-ruby-1.0.5/lib/concurrent/concern/logging.rb:20: warning: instance variable @logger not initialized
                     [2019-07-18 11:15:02.615] ERROR -- #<Double (anonymous)> received unexpected message :deliver_now with (no args): nil

                     ##teamcity[testFailed name = 'User sends a welcome email on account creation' message = '(UserMailer (class)).welcome_email(#<User id: 1, email: "tester44@example.com", created_at: "2019-07-18 09:15:02", updated_at: "2019-07-...rst_name: "Aaron", last_name: "Sumner", authentication_token: "y7v6GE3QyzFhmUCNskRD", location: nil>)|n    expected: 1 time with arguments: (#<User id: 1, email: "tester44@example.com", created_at: "2019-07-18 09:15:02", updated_at: "2019-07-...rst_name: "Aaron", last_name: "Sumner", authentication_token: "y7v6GE3QyzFhmUCNskRD", location: nil>)|n    received: 2 times with arguments: (#<User id: 1, email: "tester44@example.com", created_at: "2019-07-18 09:15:02", updated_at: "2019-07-...rst_name: "Aaron", last_name: "Sumner", authentication_token: "y7v6GE3QyzFhmUCNskRD", location: nil>)' details = '|n  0) User sends a welcome email on account creation|n     Failure/Error: expect(UserMailer).to have_received(:welcome_email).with(user)|n|n       (UserMailer (class)).welcome_email(#<User id: 1, email: "tester44@example.com", created_at: "2019-07-18 09:15:02", updated_at: "2019-07-...rst_name: "Aaron", last_name: "Sumner", authentication_token: "y7v6GE3QyzFhmUCNskRD", location: nil>)|n           expected: 1 time with arguments: (#<User id: 1, email: "tester44@example.com", created_at: "2019-07-18 09:15:02", updated_at: "2019-07-...rst_name: "Aaron", last_name: "Sumner", authentication_token: "y7v6GE3QyzFhmUCNskRD", location: nil>)|n           received: 2 times with arguments: (#<User id: 1, email: "tester44@example.com", created_at: "2019-07-18 09:15:02", updated_at: "2019-07-...rst_name: "Aaron", last_name: "Sumner", authentication_token: "y7v6GE3QyzFhmUCNskRD", location: nil>)|n     # ./spec/models/user_spec.rb:32:in `block (2 levels) in <top (required)>|'' timestamp = '2019-07-18T11:15:02.748+0200']

                     ##teamcity[testFinished name = 'User sends a welcome email on account creation' duration = '70' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302667, 1563441302597), duration=70, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.069815' timestamp = '2019-07-18T11:15:02.748+0200']

                     ##teamcity[testStarted name = 'User performs geocoding' captureStandardOutput = 'true' locationHint = 'file:///Users/user/Downloads/everydayrails-2017/spec/models/user_spec.rb:35' timestamp = '2019-07-18T11:15:02.749+0200']

                     ##teamcity[testFinished name = 'User performs geocoding' duration = '51' diagnosticInfo = 'rspec |[3.8.0|], f/s=(1563441302800, 1563441302749), duration=51, time.now=2019-07-18 11:15:02 +0200, raw|[:started_at|]=2019-07-18 11:15:02 +0200, raw|[:finished_at|]=2019-07-18 11:15:02 +0200, raw|[:run_time|]=0.051068' timestamp = '2019-07-18T11:15:02.800+0200']

                     ##teamcity[testSuiteFinished name = 'User' timestamp = '2019-07-18T11:15:02.801+0200']

                     70 examples, 2 failures, 68 passed

                     Finished in 9.303811 seconds

                     Process finished with exit code 1
                     """,
                   """
                     [stdout]Testing started at 11:14 ...
                     [stdout]/bin/bash -c "/Users/user/.rvm/bin/rvm ruby-2.5.3@everydayrails do /Users/user/.rvm/rubies/ruby-2.5.3/bin/ruby /Users/user/Downloads/everydayrails-2017/bin/rspec /Users/user/Downloads/everydayrails-2017/spec --require teamcity/spec/runner/formatter/teamcity/formatter --format Spec::Runner::Formatter::TeamcityFormatter --pattern '**/*_spec.rb' --no-color"
                     [stdout]
                     [stdout]/Users/user/.rvm/gems/ruby-2.5.3@everydayrails/gems/activesupport-5.1.1/lib/active_support/core_ext/hash/slice.rb:21: warning: method redefined; discarding old slice
                     [stdout]/Users/user/.rvm/gems/ruby-2.5.3@everydayrails/gems/activesupport-5.1.1/lib/active_support/core_ext/class/attribute.rb:90: warning: previous definition of default_url_options was here
                     [stdout]/Users/user/.rvm/gems/ruby-2.5.3@everydayrails/gems/concurrent-ruby-1.0.5/lib/concurrent/concern/logging.rb:20: warning: instance variable @logger not initialized
                     [stdout]/Users/user/.rvm/gems/ruby-2.5.3@everydayrails/gems/concurrent-ruby-1.0.5/lib/concurrent/concern/logging.rb:20: warning: instance variable @logger not initialized
                     [stdout]/Users/user/.rvm/gems/ruby-2.5.3@everydayrails/gems/concurrent-ruby-1.0.5/lib/concurrent/concern/logging.rb:20: warning: instance variable @logger not initialized
                     [stdout][2019-07-18 11:15:02.615] ERROR -- #<Double (anonymous)> received unexpected message :deliver_now with (no args): nil
                     [stdout]
                     [stdout]70 examples, 2 failures, 68 passed
                     [stdout]
                     [stdout]Finished in 9.303811 seconds
                     [stdout]
                     [stdout]Process finished with exit code 1
                     """,
                   false);
  }

  private void doCheckOutptut(String outputStr, String expected, boolean splitByLines) {
    final List<String> lines;
    if (splitByLines) {
      lines = StringUtil.split(outputStr, "\n", false);
    } else {
      lines = Collections.singletonList(outputStr);
    }
    for (String line : lines) {
      myOutputConsumer.process(line, ProcessOutputTypes.STDOUT);
    }
    myOutputConsumer.flushBufferOnProcessTermination(0);

    assertEquals(expected, myEnventsProcessor.getOutput());
  }
}
