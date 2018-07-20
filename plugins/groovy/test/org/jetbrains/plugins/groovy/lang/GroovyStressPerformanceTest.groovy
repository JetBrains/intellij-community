// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.*
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ThrowableRunnable
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrUnusedIncDecInspection
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import org.jetbrains.plugins.groovy.util.Slow
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
@Slow
class GroovyStressPerformanceTest extends LightGroovyTestCase {

  final String basePath = TestUtils.testDataPath + 'highlighting/'

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_3

  ThrowableRunnable configureAndHighlight(String text) {
    return {
      myFixture.psiManager.dropPsiCaches()
      myFixture.configureByText 'a.groovy', text
      myFixture.doHighlighting()
    } as ThrowableRunnable
  }

  void testDontWalkLongInferenceChain() throws Exception {
    //RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    Map<Integer, PsiClass> classes = [:]
    myFixture.addFileToProject "Foo0.groovy", """class Foo0 {
      def foo() { return 0 }
    }"""
    def max = 100
    for (i in 1..max) {
      def file = myFixture.addFileToProject("Foo${i}.groovy", """class Foo$i {
        def foo() { return Foo${i - 1}.foo() }
      }""")
      classes[i] = (file as GroovyFile).classes[0]
    }

    def deepFile = myFixture.addFileToProject("DeepTest.groovy", "def test() { return Foo${max}.foo() }") as GroovyFile
    assert Object.name ==  inferredType(deepFile.scriptClass, 'test')

    def shallowFile = myFixture.addFileToProject("ShallowTest.groovy", "def test() { return Foo2.foo() }") as GroovyFile
    assert Integer.name == inferredType(shallowFile.scriptClass, 'test')

    int border = (1..max).find { int i ->
      GroovyPsiManager.getInstance(project).dropTypesCache()
      return inferredType(classes[i], 'foo') == Object.name
    }

    assert border

    GroovyPsiManager.getInstance(project).dropTypesCache()
    assert inferredType(classes[border], 'foo') == Object.name
    assert inferredType(classes[border - 1], 'foo') == Integer.name
  }

  private static String inferredType(PsiClass clazz, String method) {
    final grMethod = clazz.findMethodsByName(method, false)[0] as GrMethod
    grMethod.inferredReturnType.canonicalText
  }


  void testQuickIncrementalReparse() {
    def story = '''scenario {
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
'''
    myFixture.configureByText 'a.groovy', story * 200 + "<caret>"
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    myFixture.type 'foo {}\n'
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    PlatformTestUtil.startPerformanceTest(getTestName(false), 10000, {
      story.toCharArray().each {
        myFixture.type it
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }

    } as ThrowableRunnable).assertTiming()
  }

  void testManyAnnotatedFields() {
    String text = "class Foo {\n"
    for (i in 1..10) {
      text += "@Deprecated String foo$i\n"
    }
    text += "}"

    measureHighlighting(text, 250)
  }

  private void measureHighlighting(String text, int time) {
    IdeaTestUtil.startPerformanceTest(getTestName(false), time, configureAndHighlight(text)).usesAllCPUCores().assertTiming()
  }

  void testDeeplyNestedClosures() {
    RecursionManager.assertOnRecursionPrevention(myFixture.testRootDisposable)
    String text = "println 'hi'"
    String defs = ""
    for (i in 1..10) {
      text = "foo$i { $text }"
      defs += "def foo$i(Closure cl) {}\n"
    }
    myFixture.enableInspections(new MissingReturnInspection())
    measureHighlighting(defs + text, 300)
  }

  void testDeeplyNestedClosuresInCompileStatic() {
    RecursionManager.assertOnRecursionPrevention(myFixture.testRootDisposable)

    String text = "println 'hi'"
    String defs = ""
    for (i in 1..10) {
      text = "foo$i {a = 5; $text }"
      defs += "def foo$i(Closure cl) {}\n"
    }
    myFixture.enableInspections(new MissingReturnInspection())

    addCompileStatic()
    measureHighlighting(defs + "\n @groovy.transform.CompileStatic def compiledStatically() {\ndef a = ''\n" + text + "\n}", 500)
  }

  void testDeeplyNestedClosuresInGenericCalls() {
    RecursionManager.assertOnRecursionPrevention(myFixture.testRootDisposable)
    String text = "println it"
    for (i in 1..10) {
      text = "foo(it) { $text }"
    }
    myFixture.enableInspections(new MissingReturnInspection())

    measureHighlighting("def <T> void foo(T t, Closure cl) {}\n$text", 1600)
  }

  void testDeeplyNestedClosuresInGenericCalls2() {
    RecursionManager.assertOnRecursionPrevention(myFixture.testRootDisposable)
    String text = "println it"
    for (i in 1..10) {
      text = "foo(it) { $text }"
    }
    myFixture.enableInspections(new MissingReturnInspection())
    measureHighlighting("def <T> void foo(T t, Closure<T> cl) {}\n$text", 1200)
  }

  void testManyAnnotatedScriptVariables() {
    measureHighlighting((0..100).collect { "@Anno String i$it = null" }.join("\n"), 1000)
  }

  void "test no recursion prevention when resolving supertype"() {
    RecursionManager.assertOnRecursionPrevention(myFixture.testRootDisposable)
    myFixture.addClass("interface Bar {}")
    measureHighlighting("class Foo implements Bar {}", 200)
  }

  void "test no recursion prevention when contributing constructors"() {
    RecursionManager.assertOnRecursionPrevention(myFixture.testRootDisposable)
    myFixture.addClass("interface Bar {}")
    def text = """
@groovy.transform.TupleConstructor
class Foo implements Bar {
  int a
  Foo b
  int getBar() {}
  void setBar(int bar) {}
  void someMethod(int a = 1) {}
}"""
    measureHighlighting(text, 200)
  }

  void "test using non-reassigned for loop parameters"() {
    RecursionManager.assertOnRecursionPrevention(myFixture.testRootDisposable)
    def text = """
def foo(List<File> list) {
  for (file in list) {
${
"   println bar(file)\n" * 100
}
  }
}
def bar(File file) { file.path }
"""
    measureHighlighting(text, 2000)
  }

  void "test using SSA variables in a for loop"() {
    def text = """
def foo(List<String> list, SomeClass sc) {
  List<String> result
  for (s in list) {
${
'''
    bar(s, result)
    bar2(s, result, sc)
    bar3(foo:s, bar:result, sc)
    sc.someMethod(s)
''' * 100
    }
  }
}
def bar(String s, List<String> result) { result << s }
def bar2(String s, List<String> result) { result << s }
def bar2(int s, List<String> result, SomeClass sc) { result << s as String }
def bar3(Map args, List<String> result, SomeClass sc) { result << s as String }

class SomeClass {
  void someMethod(String s) {}
}
"""
    measureHighlighting(text, 14_000)
  }

  void "test infer only the variable types that are needed"() {
    addGdsl '''contribute(currentType(String.name)) {
  println 'sleeping'
  Thread.sleep(100_000)
  method name:'foo', type:String, params:[:], namedParams:[
    parameter(name:'param1', type:String),
  ]
}'''
    def text = '''
  String s = "abc"
while (true) {
  s = "str".foo(s)
  File f = new File('path')
  f.canoPath<caret>
}
'''
    PlatformTestUtil.startPerformanceTest(getTestName(false), 20_000, configureAndComplete(text)).attempts(1).usesAllCPUCores().assertTiming()
  }

  void testClosureRecursion() {
    def text = '''
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
'''
    measureHighlighting(text, 700)
  }

  ThrowableRunnable configureAndComplete(String text) {
    return {
      myFixture.configureByText 'a.groovy', text
      myFixture.completeBasic()
    } as ThrowableRunnable
  }

  private def addGdsl(String text) {
    final PsiFile file = myFixture.addFileToProject("Enhancer.gdsl", text)
    GroovyDslFileIndex.activate(file.virtualFile)
  }

  @CompileStatic
  void "test performance of resolving methods with many siblings"() {
    int classMethodCount = 50000
    assert myFixture.addClass("""class Foo {
${(1..classMethodCount).collect({"void foo${it}() {}"}).join("\n")}
}""")

    def refCountInBlock = 50
    def blockCount = 10
    def methodBody = (1..refCountInBlock).collect({ "foo$it()" }).join("\n")
    String text = "class Bar extends Foo { " +
                  (0..blockCount).collect({ "def zoo$it() {\n" + methodBody + "\n}"}).join("\n") +
                  "}"
    myFixture.configureByText('a.groovy', '')
    assert myFixture.file instanceof GroovyFile
    PlatformTestUtil.startPerformanceTest('many siblings', 1000, {
      // clear caches
      WriteCommandAction.runWriteCommandAction(project) {
        myFixture.editor.document.text = ""
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.editor.document.text = text
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }

      def refs = SyntaxTraverser.psiTraverser(myFixture.file).filter(GrReferenceElement).toList()
      assert refs.size() > refCountInBlock * blockCount
      for (ref in refs) {
        assert ref.resolve(): ref.text
      }
    }).attempts(2).assertTiming()
  }

  @CompileStatic
  void testVeryLongDfaWithComplexGenerics() {
    def configure = { int i ->
      myFixture.configureByText "a${i}.groovy", """\
class TroubleCase$i {
  private Foo$i<Bar$i> fooBar;
  private Foo$i<Baz$i> fooBaz;

  private void troubleMethod(boolean b) {
    def <warning descr="Assignment is not used">icDao</warning> = (b?fooBaz:fooBar);
    for(Object x: new ArrayList()) {}
  }
}

public interface Foo$i<FFIC$i> {}
public class Bar$i implements Cloneable, Zoo$i<Goo$i, Doo$i, Coo$i, Woo$i> {}
public interface Zoo$i<AR$i, FR$i, AM$i extends Hoo$i<AR$i>, FM$i extends Hoo$i<FR$i>> {}
public interface Hoo$i<R$i> {}
public class Baz$i implements Cloneable, Zoo$i<String,String,Too$i,Yoo$i> {}
public class Goo$i {}
public class Too$i implements Hoo$i<String> {}
public class Coo$i implements Serializable, Cloneable, Hoo$i<Goo$i> {}
public class Woo$i implements Serializable, Cloneable, Hoo$i<Doo$i> {}
public class Yoo$i implements Serializable, Cloneable, Hoo$i<String> {}
public class Doo$i {}
"""
    }
    IdeaTestUtil.startPerformanceTest("testing dfa", 800, {
      myFixture.checkHighlighting true, false, false
    }).setup({
      myFixture.enableInspections GroovyAssignabilityCheckInspection, UnusedDefInspection, GrUnusedIncDecInspection
      configure 1
      myFixture.checkHighlighting true, false, false
      configure 2
    }).attempts(1).assertTiming()
  }

  void 'test resolve long chains'() {
    def header = """\
class Node {
  public Node nn
  public Node nn() {}
  public Node plus(Node n) {n}
}
def a = new Node()
"""
    def data = [
      "a${'.nn' * 500}.nn",               // a.nn.nn ... .nn
      "a${'.nn()' * 250}.nn",             // a.nn() ... .nn().nn
      "a${' += a' * 500} += new Node()",  // a += a ... += a += new Node()
    ]
    for (expressionText in data) {
      def file = (GroovyFile)fixture.configureByText('_.groovy', header + expressionText)
      def reference = (PsiReference)file.statements.last()
      assert reference.resolve() != null
    }
  }

  void "test do not resolve LHS and RHS of assignment when name doesn't match"() {
    def text = new StringBuilder("a0 = 1\n")
    def n = 1000
    for (i in 1..n) {
      text.append "a$i = a${i - 1}\n"
    }
    text.append "a$n"
    def file = (GroovyFile)fixture.configureByText('_.groovy', text.toString())
    def last = (GrReferenceExpression)file.statements.last()
    assert last.resolve() instanceof GrBindingVariable
  }
}
