// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrUnusedIncDecInspection;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;
import org.jetbrains.plugins.groovy.util.Slow;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slow
public class GroovyStressPerformanceTest extends LightGroovyTestCase {
  @Override
  public String getBasePath() {
    return TestUtils.getTestDataPath() + "highlighting/";
  }

  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_3;
  }

  public ThrowableRunnable configureAndHighlight(String text) {
    return () -> {
      myFixture.getPsiManager().dropPsiCaches();
      myFixture.configureByText("a.groovy", text);
      myFixture.doHighlighting();
    };
  }

  public void testDontWalkLongInferenceChain() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    Map<Integer, PsiClass> classes = new LinkedHashMap<>();
    myFixture.addFileToProject("Foo0.groovy", """
      class Foo0 {
         def foo() { return 0 }
      }
      """);
    int max = 100;
    for (int i = 1; i <= 100; i++) {
      PsiFile file = myFixture.addFileToProject("Foo" + i + ".groovy",
                                                "class Foo" + i + " {\n" + "    def foo() { return Foo" + (i - 1) + ".foo() }\n" + " }");
      classes.put(i, ((GroovyFile)file).getClasses()[0]);
    }

    GroovyFile deepFile = (GroovyFile)myFixture.addFileToProject("DeepTest.groovy", "def test() { return Foo" + max + ".foo() }");
    assert Object.class.getName().equals(inferredType(deepFile.getScriptClass(), "test"));

    GroovyFile shallowFile =
      DefaultGroovyMethods.asType(myFixture.addFileToProject("ShallowTest.groovy", "def test() { return Foo2.foo() }"), GroovyFile.class);
    assert Integer.class.getName().equals(inferredType(shallowFile.getScriptClass(), "test"));

    List<PsiClass> values = classes.values().stream().toList();

    int border = ContainerUtil.indexOf(values, (clazz) -> {
      PsiManager.getInstance(getProject()).dropPsiCaches();
      return inferredType(clazz, "foo").equals(Object.class.getName());
    }) + 1;

    PsiManager.getInstance(getProject()).dropPsiCaches();
    assert inferredType(classes.get(border), "foo").equals(Object.class.getName());
    assert inferredType(classes.get(border - 1), "foo").equals(Integer.class.getName());
  }

  private static String inferredType(PsiClass clazz, String method) {
    GrMethod grMethod = DefaultGroovyMethods.asType(clazz.findMethodsByName(method, false)[0], GrMethod.class);
    return grMethod.getInferredReturnType().getCanonicalText();
  }

  public void testQuickIncrementalReparse() {
    String story = """
      scenario {
        given "some precondition", {
          // do something
        }
        when "I do some stuff", {
          // foo bar code
        }
        then "something I expect happens", {
          // some verification
        }
      }
      """;
    myFixture.configureByText("a.groovy", StringGroovyMethods.multiply(story, 200) + "<caret>");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    myFixture.type("foo {}\n");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    Benchmark.newBenchmark(getTestName(false), () -> {
      for (char c : story.toCharArray()) {
        myFixture.type(c);
        PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      }
    }).start();
  }

  public void testManyAnnotatedFields() {
    String text = "class Foo {\n";
    for (int i = 0; i < 10; i++) {
      text += "@Deprecated String foo" + i + "\n";
    }

    text += "}";
    measureHighlighting(text);
  }

  private void measureHighlighting(String text) {
    Benchmark.newBenchmark(getTestName(false), configureAndHighlight(text)).start();
  }

  public void testDeeplyNestedClosures() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    String text = "println 'hi'";
    String defs = "";
    for (int i = 0; i < 10; i++) {
      text = "foo" + i + " { " + text + " }";
      defs += "def foo" + i + "(Closure cl) {}\n";
    }

    myFixture.enableInspections(new MissingReturnInspection());
    measureHighlighting(defs + text);
  }

  public void testDeeplyNestedClosuresInCompileStatic() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());

    String text = "println 'hi'";
    String defs = "";
    for (int i = 0; i < 10; i++) {
      text = "foo" + i + " {a = 5; " + text + " }";
      defs += "def foo" + i + "(Closure cl) {}\n";
    }

    myFixture.enableInspections(new MissingReturnInspection());

    addCompileStatic();
    measureHighlighting(defs + "\n @groovy.transform.CompileStatic def compiledStatically() {\ndef a = ''\n" + text + "\n}");
  }

  public void testDeeplyNestedClosuresInGenericCalls() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    String text = "println it";
    for (int i = 0; i < 10; i++) {
      text = "foo(it) { " + text + " }";
    }
    myFixture.enableInspections(new MissingReturnInspection());
    measureHighlighting("def <T> void foo(T t, Closure cl) {}\n" + text);
  }

  public void testDeeplyNestedClosuresInGenericCalls2() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    String text = "println it";
    for (int i = 0; i < 10; i++) {
      text = "foo(it) { " + text + " }";
    }
    myFixture.enableInspections(new MissingReturnInspection());
    measureHighlighting("def <T> void foo(T t, Closure<T> cl) {}\n" + text);
  }

  public void testManyAnnotatedScriptVariables() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      sb.append("@Anno String i").append(i).append(" = null\n");
    }
    measureHighlighting(sb.toString());
  }

  public void test_no_recursion_prevention_when_resolving_supertype() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    myFixture.addClass("interface Bar {}");
    measureHighlighting("class Foo implements Bar {}");
  }

  public void test_no_recursion_prevention_when_contributing_constructors() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    myFixture.addClass("interface Bar {}");
    String text = """
      
      @groovy.transform.TupleConstructor
      class Foo implements Bar {
        int a
        Foo b
        int getBar() {}
        void setBar(int bar) {}
        void someMethod(int a = 1) {}
      }""";
    measureHighlighting(text);
  }

  public void test_using_non_reassigned_for_loop_parameters() {
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
    String text = """
                    def foo(List<File> list) {
                      for (file in list) {
                    """ + StringGroovyMethods.multiply("   println bar(file)\n", 100) + """
                    
                      }
                    }
                    def bar(File file) { file.path }
                    """;
    measureHighlighting(text);
  }

  public void test_using_SSA_variables_in_a_for_loop() {
    String text = """
                    
                    def foo(List<String> list, SomeClass sc) {
                      List<String> result
                      for (s in list) {
                    """ + StringGroovyMethods.multiply("""
                                                         
                                                             bar(s, result)
                                                             bar2(s, result, sc)
                                                             bar3(foo:s, bar:result, sc)
                                                             sc.someMethod(s)
                                                         """, 100) + """
                    
                      }
                    }
                    def bar(String s, List<String> result) { result << s }
                    def bar2(String s, List<String> result) { result << s }
                    def bar2(int s, List<String> result, SomeClass sc) { result << s as String }
                    def bar3(Map args, List<String> result, SomeClass sc) { result << s as String }
                    
                    class SomeClass {
                      void someMethod(String s) {}
                    }
                    """;
    measureHighlighting(text);
  }

  public void test_constructor_call_s() {
    String text = """
                    class Cl {
                      Cl(Map<String, Integer> a, Condition<Cl> con, String s) { }
                    
                      interface Condition<T> {}
                    
                      static <T> Condition<T> alwaysFalse() {
                          return (Condition<T>)null
                      }
                    
                    
                      static m() {
                         \s""" + "new Cl(alwaysFalse(), name: 1, m: 2, new Object().toString(), sad: 12)".repeat(100) + """
                        }
                      }
                    """;
    Benchmark.newBenchmark(getTestName(false), configureAndHighlight(text)).attempts(20).start();
  }

  public void test_infer_only_the_variable_types_that_are_needed() {
    addGdsl("""
              contribute(currentType(String.name)) {
                println 'sleeping'
                Thread.sleep(100_000)
                method name:'foo', type:String, params:[:], namedParams:[
                  parameter(name:'param1', type:String),
                ]
              }""");
    String text = """
         String s = "abc"
         while (true) {
           s = "str".foo(s)
           File f = new File('path')
           f.canoPath<caret>
         }
      """;
    Benchmark.newBenchmark(getTestName(false), configureAndComplete(text)).attempts(1).start();
  }

  public void testClosureRecursion() {
    String text = """
      class AwsService {
          def grailsApplication
          def configService
      
          def rdsTypeTranslation = [
                  "udbInstClass.uDBInst" : "db.t1.micro",
                  "dbInstClass.uDBInst" : "db.t1.micro",
                  "dbInstClass.smDBInst" : "db.m1.small",
                  "dbInstClass.medDBInst" : "db.m1.medium",
                  "dbInstClass.lgDBInst" : "db.m1.large",
                  "dbInstClass.xlDBInst" : "db.m1.xlarge",
                  "hiMemDBInstClass.xlDBInst" : "db.m2.xlarge",
                  "hiMemDBInstClass.xxlDBInst" : "db.m2.2xlarge",
                  "hiMemDBInstClass.xxxxDBInst" : "db.m2.4xlarge",
                  "multiAZDBInstClass.uDBInst" : "db.t1.micro",
                  "multiAZDBInstClass.smDBInst" : "db.m1.small",
                  "multiAZDBInstClass.medDBInst" : "db.m1.medium",
                  "multiAZDBInstClass.lgDBInst" : "db.m1.large",
                  "multiAZDBInstClass.xlDBInst" : "db.m1.xlarge",
                  "multiAZHiMemInstClass.xlDBInst" : "db.m2.xlarge",
                  "multiAZHiMemInstClass.xxlDBInst" : "db.m2.2xlarge",
                  "multiAZHiMemInstClass.xxxxDBInst" : "db.m2.4xlarge"]
      
          def regionTranslation = [
                  'us-east-1' : 'us-east',
                  'us-west-2' : 'us-west-2',
                  'us-west-1' : 'us-west',
                  'eu-west-1' : 'eu-ireland',
                  'ap-southeast-1' : 'apac-sin',
                  'ap-northeast-1' : 'apac-tokyo',
                  'sa-east-1' : 'sa-east-1']
      
          def price(env) {
              def priceMap = [:]
              def region = env.region
      
              def aws = new AwsApi(configService.getAwsConfiguration(), env, configService.getTempPath())
              def price = 0.0
              def count = 0
      
              //def ec2EbsPricing = aws.getEbsOptimizedComputePricing()
              def rdsMySqlPricing
              def rdsMySqlMultiPricing
              def rdsOraclePricing
              try {
                  rdsMySqlPricing = aws.getMySqlPricing()
                  rdsMySqlMultiPricing = aws.getMySqlMultiAZPricing()
                  rdsOraclePricing = aws.getOracleLIPricing()
              } catch (Exception) {
                  //TODO : Find new rds pricing json
              }
              //def elbPricing = aws.getELBPricing()
              def ebsPricing = aws.getEBSPricing()
      
              aws.getComputeResponse(region).reservations.each { Reservation inst ->
                  inst.instances.each { Instance it ->
                      if (it.state.code.toInteger() == 16) {
                          def os
                          switch (it.platform) {
                              case 'windows':
                                  os = "mswin"
                                  break;
                              case 'linux':
                              default:
                                  os = "linux"
                                  break;
                          }
      
                          aws.getComputePricing(os).config.regions.each { pricingRegion ->
                              if (pricingRegion.region == regionTranslation[region]) {
                                  pricingRegion.instanceTypes.each { instanceType ->
                                      instanceType.sizes.each { size ->
                                          if (size.size == it.instanceType) {
                                              size.valueColumns.each { valueColumn ->
                                                  if (valueColumn.name == os) {
                                                      //Price by type
                                                      def key = "price-ec2-" + os + "-" + it.instanceType
                                                      if (!priceMap.containsKey(key)){
                                                          priceMap[key] = 0.0
                                                      }
                                                      priceMap[key] += valueColumn.prices.USD.toFloat()
      
                                                      //Type count
                                                      key = "count-ec2-" + os + "-" + it.instanceType
                                                      if (!priceMap.containsKey(key)){
                                                          priceMap[key] = 0
                                                      }
                                                      priceMap[key] += 1
                                                      count++
      
                                                      //Price for all
                                                      if (!priceMap.containsKey("price-ec2")){
                                                          priceMap["price-ec2"] = 0.0
                                                      }
                                                      priceMap["price-ec2"] += valueColumn.prices.USD.toFloat()
      
                                                      //Total
                                                      price += valueColumn.prices.USD.toFloat()
                                                  }
                                              }
                                          }
                                      }
                                  }
                              }
                          }
                      }
                  }
              }
      
              def rdsPrice = 0.0
              aws.getRDSResponse(region).dBInstances.each { DBInstance it ->
                  def json
                  switch (it.engine) {
                      case 'mysql':
                          if (it.multiAZ) {
                              json = rdsMySqlMultiPricing
                          } else {
                              json = rdsMySqlPricing
                          }
                          break;
                      case 'oracle-se1':
                          json = rdsOraclePricing
                          break;
                  }
      
                  if (json != null) {
                      json.config.regions.each { pricingRegion ->
                          if (pricingRegion.region == regionTranslation[region]) {
                              pricingRegion.types.each { instanceType ->
                                  instanceType.tiers.each { tier ->
                                      if (rdsTypeTranslation[instanceType.name + "." + tier.name] == it.DBInstanceClass) {
                                          rdsPrice += tier.prices.USD.toFloat()
                                          price += tier.prices.USD.toFloat()
                                      }
                                  }
                              }
                          }
                      }
                  }
              }
      
              //TODO : IOPS
              def ebsPrice = 0.0
              aws.getEBSResponse(region).volumes.each { Volume it ->
                  ebsPricing.config.regions.each { pricingRegion ->
                      if (pricingRegion.region == regionTranslation[region]) {
                          pricingRegion.types.each { ebsType ->
                              if (ebsType.name == "ebsVols") {
                                  ebsType.values.each { ebsValue ->
                                      if (ebsValue.rate == "perGBmoProvStorage") {
                                          ebsPrice += (ebsValue.prices.USD.toFloat() * it.size.toFloat() / 30 / 24)
                                          price += (ebsValue.prices.USD.toFloat() * it.size.toFloat() / 30 / 24)
                                      }
                                  }
                              }
                          }
                      }
                  }
              }
      
              priceMap.put("price-total", price)
              priceMap.put("price-rds", rdsPrice)
              priceMap.put("price-ebs", ebsPrice)
              priceMap.put("count-total", count)
              return priceMap
          }
      }
      """;
    measureHighlighting(text);
  }

  public ThrowableRunnable configureAndComplete(String text) {
    return () -> {
      myFixture.configureByText("a.groovy", text);
      myFixture.completeBasic();
    };
  }

  private void addGdsl(String text) {
    PsiFile file = myFixture.addFileToProject("Enhancer.gdsl", text);
    GroovyDslFileIndex.activate(file.getVirtualFile());
  }

  public void test_performance_of_resolving_methods_with_many_siblings() {
    int classMethodCount = 50000;
    StringBuilder fooBodyBuilder = new StringBuilder();
    for (int i = 0; i < classMethodCount; i++) {
      fooBodyBuilder.append("\tvoid foo").append(i).append("() {}\n");
    }
    myFixture.addClass("class Foo {\n" + fooBodyBuilder + "}");

    int refCountInBlock = 50;
    int blockCount = 10;
    StringBuilder methodBodyBldr = new StringBuilder();
    for (int i = 0; i < refCountInBlock; i++) {
      methodBodyBldr.append("\t\tfoo").append(i).append("()\n");
    }
    StringBuilder barBodyBuilder = new StringBuilder();
    barBodyBuilder.append("class Bar extends Foo {\n");
    for (int i = 0; i < blockCount; i++) {
      barBodyBuilder.append("\tdef zoo").append(i).append("() {\n").append(methodBodyBldr).append("}\n");
    }
    barBodyBuilder.append("}");
    myFixture.configureByText("a.groovy", "");
    assert myFixture.getFile() instanceof GroovyFile;
    Benchmark.newBenchmark("many siblings", () -> {
      // clear caches
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        myFixture.getEditor().getDocument().setText("");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        myFixture.getEditor().getDocument().setText(barBodyBuilder.toString());
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      });
      List<GrReferenceElement> refs = SyntaxTraverser.psiTraverser(myFixture.getFile()).filter(GrReferenceElement.class).toList();
      assert refs.size() > refCountInBlock * blockCount;
      for (GrReferenceElement ref : refs) {
        assertNotNull(ref.resolve());
      }
    }).attempts(2).start();
  }

  private void configureTroubleClass(int i) {
    myFixture.configureByText("a" + i + ".groovy", """
                                                     class TroubleCase""" +
                                                   i +
                                                   """
                                                      {
                                                       private Foo\
                                                     """ +
                                                   i +
                                                   "<Bar" +
                                                   i +
                                                   """
                                                     > fooBar;
                                                       private Foo""" +
                                                   i +
                                                   "<Baz" +
                                                   i +
                                                   """
                                                     > fooBaz;
                                                     
                                                       private void troubleMethod(boolean b) {
                                                         def <warning descr="Assignment is not used">icDao</warning> = (b?fooBaz:fooBar);
                                                         for(Object x: new ArrayList()) {}
                                                       }
                                                     }
                                                     
                                                     public interface Foo""" +
                                                   i +
                                                   "<FFIC" +
                                                   i +
                                                   """
                                                     > {}
                                                     public class Bar""" +
                                                   i +
                                                   " implements Cloneable, Zoo" +
                                                   i +
                                                   "<Goo" +
                                                   i +
                                                   ", Doo" +
                                                   i +
                                                   ", Coo" +
                                                   i +
                                                   ", Woo" +
                                                   i +
                                                   """
                                                     > {}
                                                     public interface Zoo""" +
                                                   i +
                                                   "<AR" +
                                                   i +
                                                   ", FR" +
                                                   i +
                                                   ", AM" +
                                                   i +
                                                   " extends Hoo" +
                                                   i +
                                                   "<AR" +
                                                   i +
                                                   ">, FM" +
                                                   i +
                                                   " extends Hoo" +
                                                   i +
                                                   "<FR" +
                                                   i +
                                                   """
                                                     >> {}
                                                     public interface Hoo""" +
                                                   i +
                                                   "<R" +
                                                   i +
                                                   """
                                                     > {}
                                                     public class Baz""" +
                                                   i +
                                                   " implements Cloneable, Zoo" +
                                                   i +
                                                   "<String,String,Too" +
                                                   i +
                                                   ",Yoo" +
                                                   i +
                                                   """
                                                     > {}
                                                     public class Goo""" +
                                                   i +
                                                   """
                                                      {}
                                                     public class Too""" +
                                                   i +
                                                   " implements Hoo" +
                                                   i +
                                                   """
                                                     <String> {}
                                                     public class Coo""" +
                                                   i +
                                                   " implements Serializable, Cloneable, Hoo" +
                                                   i +
                                                   "<Goo" +
                                                   i +
                                                   """
                                                     > {}
                                                     public class Woo""" +
                                                   i +
                                                   " implements Serializable, Cloneable, Hoo" +
                                                   i +
                                                   "<Doo" +
                                                   i +
                                                   """
                                                     > {}
                                                     public class Yoo""" +
                                                   i +
                                                   " implements Serializable, Cloneable, Hoo" +
                                                   i +
                                                   """
                                                     <String> {}
                                                     public class Doo""" +
                                                   i +
                                                   """
                                                      {}
                                                     """);
  }

  public void testVeryLongDfaWithComplexGenerics() {
    Benchmark.newBenchmark("testing dfa", () -> {
      myFixture.checkHighlighting(true, false, false);
    }).setup(() -> {
      myFixture.enableInspections(GroovyAssignabilityCheckInspection.class, UnusedDefInspection.class, GrUnusedIncDecInspection.class);
      configureTroubleClass(1);
      myFixture.checkHighlighting(true, false, false);
      configureTroubleClass(2);
    }).attempts(1).start();
  }

  public void test_resolve_long_chain_of_references() {
    String header = """
      class Node {
        public Node nn
      }
      def a = new Node()
      """;
    // a.nn.nn ... .nn
    GroovyFile file = (GroovyFile)getFixture().configureByText("_.groovy", header + "a" + StringGroovyMethods.multiply(".nn", 500) + ".nn");
    GrReferenceExpression reference = (GrReferenceExpression)DefaultGroovyMethods.last(file.getStatements());
    assert reference.resolve() != null;
  }

  public void test_resolve_long_chain_of_method_calls() {
    String header = """
      class Node {
        public Node nn
        public Node nn() {}
      }
      def a = new Node()
      """;
    // a.nn() ... .nn().nn
    GroovyFile file =
      (GroovyFile)getFixture().configureByText("_.groovy", header + "a" + StringGroovyMethods.multiply(".nn()", 250) + ".nn");
    GrReferenceExpression reference = (GrReferenceExpression)DefaultGroovyMethods.last(file.getStatements());
    assert reference.resolve() != null;
  }

  public void test_resolve_long_chain_of_operators() {
    String header = """
      class Node {
        public Node plus(Node n) {n}
      }
      def a = new Node()
      """;
    // a += a ... += a += new Node()
    GroovyFile file =
      (GroovyFile)getFixture().configureByText("_.groovy", header + "a" + StringGroovyMethods.multiply(" += a", 500) + " += new Node()");
    GroovyCallReference reference = ((GrAssignmentExpression)DefaultGroovyMethods.last(file.getStatements())).getReference();
    assert reference.resolve() != null;
  }

  public void test_do_not_resolve_LHS_and_RHS_of_assignment_when_name_doesn_t_match() {
    StringBuilder text = new StringBuilder("a0 = 1\n");
    int n = 1000;
    for (int i = 1; i <= n; i++) {
      text.append("a").append(i).append(" = a").append(i - 1).append("\n");
    }

    text.append("a").append(n);
    GroovyFile file = (GroovyFile)getFixture().configureByText("_.groovy", text.toString());
    GrReferenceExpression last = (GrReferenceExpression)DefaultGroovyMethods.last(file.getStatements());
    assertInstanceOf(last.resolve(), GrBindingVariable.class);
  }

  public void test_method_processing_does_not_depend_on_the_number_of_other_methods() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    StringBuilder builder = new StringBuilder();
    int n = 1000;
    builder.append("def foo0 (a) { a }\n");
    for (int i = 0; i <= n; i++) {
      builder.append("foo").append(n).append("(a) {\n  foo").append(n - 1).append("(1)\n }");
    }
    GroovyFile file = DefaultGroovyMethods.asType(getFixture().configureByText("_.groovy", builder.toString()), GroovyFile.class);
    Benchmark.newBenchmark(getTestName(false), () -> {
      myFixture.getPsiManager().dropPsiCaches();
      ((GrExpression)DefaultGroovyMethods.last(DefaultGroovyMethods.last(file.getMethods()).getBlock().getStatements())).getType();
    }).attempts(5).start();
  }

  public void test_complex_DFA_with_a_lot_of_closures() {
    getFixture().configureByFile("stress/dfa.groovy");
    Benchmark.newBenchmark(getTestName(false), () -> {
      myFixture.getPsiManager().dropPsiCaches();
      myFixture.doHighlighting();
    }).attempts(10).start();
  }
}
