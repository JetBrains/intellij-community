package com.intellij.grazie.pro

import ai.grazie.nlp.langs.Language
import ai.grazie.rules.de.GermanParameters
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertNotEmpty
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("NonAsciiCharacters")
class HighlightingTest : BaseTestCase() {

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(GrazieInspection::class.java, GrazieInspection.Grammar::class.java, GrazieInspection.Style::class.java)
  }

  private fun configureByText(name: String, text: String) {
    myFixture.configureByText(name, text)
  }

  @NeedsCloud
  @Test
  fun `test MLEC and rules md all languages`() {
    enableLanguages(setOf(Lang.AMERICAN_ENGLISH, Lang.RUSSIAN), project, testRootDisposable)

    configureByText("a.md", """
     Hello. I'm <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.ARTICLE_ISSUES">a </GRAMMAR_ERROR>very humble <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.ARTICLE_ISSUES">persons</GRAMMAR_ERROR>.
     I'll do this <GRAMMAR_ERROR descr="Grazie.MLEC.En.All: Incorrect preposition">in</GRAMMAR_ERROR> Tuesday.
     Mary lost control <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.POLARITY">at all</GRAMMAR_ERROR>, so she went there :)✔✓
     
     two <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.QUANTIFIER_NOUN_COMPATIBILITY">dog</GRAMMAR_ERROR>
      
     two <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.QUANTIFIER_NOUN_COMPATIBILITY">sentence</GRAMMAR_ERROR>
     
     In [IntelliJ IDEA](https://github.com/JetBrains/intellij-community) and some other [JetBrains](https://www.jetbrains.com/) IDEs, *jetCheck* is used for checking that:
     * code completion suggests whatever you want to write, doesn't contain duplicates and doesn't fail
     
     А этот абзац мы напишем *по-русски*.  Собака <GRAMMAR_ERROR descr="Grazie.RuleEngine.Ru.Grammar.AGREEMENT">пошел</GRAMMAR_ERROR> гулять.
     
     А еще мы проверим букву ё.
     А <GRAMMAR_ERROR descr="Grazie.RuleEngine.Ru.Grammar.PARTICLE_SEPARATION">так же</GRAMMAR_ERROR> 
     мы <GRAMMAR_ERROR descr="Grazie.RuleEngine.Ru.Punctuation.INTER_CLAUSE_COMMA">проверим</GRAMMAR_ERROR> что работает 
     <GRAMMAR_ERROR descr="Grazie.RuleEngine.Ru.Punctuation.INTER_CLAUSE_COMMA">пунктуация</GRAMMAR_ERROR> 
     и посмотрим в <GRAMMAR_ERROR descr="Grazie.RuleEngine.Ru.Grammar.AGREEMENT">управлением</GRAMMAR_ERROR>.
     """.trimIndent())
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun `test MLEC and rules md english only`() {
    enableLanguages(setOf(Lang.AMERICAN_ENGLISH), project, testRootDisposable)

    configureByText("a.md", """
      Hello. I'm <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.ARTICLE_ISSUES">a </GRAMMAR_ERROR>very humble <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.ARTICLE_ISSUES">persons</GRAMMAR_ERROR>.
      I'll do this <GRAMMAR_ERROR descr="Grazie.MLEC.En.All: Incorrect preposition">in</GRAMMAR_ERROR> Tuesday.
      Mary lost control <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.POLARITY">at all</GRAMMAR_ERROR>, so she went there.
      
      In [IntelliJ IDEA](https://github.com/JetBrains/intellij-community) and some other [JetBrains](https://www.jetbrains.com/) IDEs, *jetCheck* is used for checking that:
      * code completion suggests whatever you want to write, doesn't contain duplicates and doesn't fail
      * the sentence is represented as a list of tokens,
      
      А этот абзац мы напишем по-русски. Собака пошел гулять.
      
      А еще мы проверим букву ё. А так же мы проверим что работает пунктуация и согласова
     """.trimIndent())
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun `test German`() {
    enableLanguages(setOf(Lang.GERMANY_GERMAN), project, testRootDisposable)
    GrazieConfig.update { it.withParameter(TextStyleDomain.Other, Language.GERMAN, GermanParameters.GENDERN_STYLE, "star") }
    configureByText("a.md", """
      Mein Vater arbeitet <GRAMMAR_ERROR descr="Grazie.RuleEngine.De.Punctuation.IN_CLAUSE_COMMA">viel</GRAMMAR_ERROR> aber mag mit uns Zeit verbringen.
      Ich gehe <GRAMMAR_ERROR descr="Grazie.MLEC.De.All: Falsche Präpositionsform">zu</GRAMMAR_ERROR> Restaurant. 
      Anna kommt <GRAMMAR_ERROR descr="Grazie.RuleEngine.De.Grammar.PREPOSITION_ISSUES">Deutschland</GRAMMAR_ERROR>.
      Ich erinnere mich <GRAMMAR_ERROR descr="Grazie.MLEC.De.All: Falsche Präposition">über</GRAMMAR_ERROR> unsere Reise.
      Wir grillen, wann das Wetter gut ist.
      Ich bin <GRAMMAR_ERROR descr="Grazie.MLEC.De.All: Überflüssige Präposition">in</GRAMMAR_ERROR> 1985 geboren.
      Dis sind die <STYLE_SUGGESTION descr="Grazie.RuleEngine.De.Style.GENDERN_STYLE">Schüler:innen</STYLE_SUGGESTION>.
     """.trimIndent())
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun `test Ukrainian`() {
    enableLanguages(setOf(Lang.UKRAINIAN), project, testRootDisposable)
    configureByText("a.md", """
      До наступної <STYLE_SUGGESTION descr="Українською правильно писати «зупинка»">останівки</STYLE_SUGGESTION> ми їхали мовчки.
      Я й не <GRAMMAR_ERROR descr="Граматична помилка">думав що комп'ютерна лінгвістика</GRAMMAR_ERROR> це легко.
     """.trimIndent())
    configureByText("a.md", """
      До наступної <STYLE_SUGGESTION descr="Grazie.RuleEngine.Uk.Style.RUSSIAN_CALQUE">останівки</STYLE_SUGGESTION> ми їхали мовчки.
     """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun `test only LT results for language with no TREE or MLEC support`() {
    enableLanguages(setOf(Lang.AMERICAN_ENGLISH, Lang.GREEK), project, testRootDisposable)
    configureByText("a.txt", """
     <GRAMMAR_ERROR descr="GREEK_REDUNDANT_2">Άρα λοιπόν</GRAMMAR_ERROR> πρακτικά το πεδίο αυτό περιέχει τον μέγιστο αριθμό κόμβων από τους οποίους πρέπει να περάσει το πακέτο έως ότου τελικά παραδοθεί στον παραλήπτη.
     """.trimIndent())
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun testGEC_LT_Rules_java() {
    GrazieConfig.update { it.copy(checkingContext = it.checkingContext.copy(isCheckInStringLiteralsEnabled = true)) }

    configureByText("a.java",
      """
    //Hello. I <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.SUBJECT_VERB_AGREEMENT">are</GRAMMAR_ERROR> <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.ARTICLE_ISSUES">a </GRAMMAR_ERROR>very humble
    //<GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.ARTICLE_ISSUES">persons</GRAMMAR_ERROR>.
    // I need your <GRAMMAR_ERROR descr="NEED_HELPS">helps</GRAMMAR_ERROR>.
    
    // and this is a comment of several sentences. we should report casing & punctuation problems here (but we don't for now)
    
    // used in super constructor
    
    // No inclusivity checks in comments, so no warning here: Sets Up Dummy Data We Can Overwrite

    /**
    * I'll do this <GRAMMAR_ERROR descr="Grazie.MLEC.En.All: Incorrect preposition">in</GRAMMAR_ERROR> Tuesday next week.
    * {@code X}: {@code X}: it's OK to be {@code ignoring mistakes around unknown fragments}.
    */
    class C {
      //todo this should go away later
      //noinspection unchecked
      int foo() {
        <error descr="Cannot resolve symbol 'System'">System</error>.out.println("; This is <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.ARTICLE_ISSUES">an </GRAMMAR_ERROR>poor starting character");
        
        // if we have reached this point, just return
        return 1;
      }
      
      // Might be null for synthetic methods like JSP page. Should I <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Punctuation.MISSING_QUESTION_MARK">go</GRAMMAR_ERROR>
    }
    /**/
      
      
    /**
     * <p>I have <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.ARTICLE_ISSUES">a </GRAMMAR_ERROR>apple and a dog at home. 
     *
     * <p>Something</p>
     */
  """)
    myFixture.checkHighlighting()
  }

  @Test
  fun `test no javadoc snippet false positives`() {
    checkCloudAndLocal("a.java", """
      /**
       * The following code shows how to use {@code Optional.isPresent}:
       * {@snippet :
       * if (v.isPresent()) {
       *     System.out.println("v: " + v.get());
       * }
       * }
       */
       class C {}
    """.trimIndent())
  }

  @Test
  fun `test allow NPs in doc comments`() {
    checkCloudAndLocal("a.kt", """
      /** A service available for adding. */
      
      /** An extension allowing to merge adjacent severity-based icons in the editor's code analysis indicator */
      
      /** The maximum number of requested forms possibly generated by adding only flags to this state */
          """)
  }

  @Test
  fun `test allow subject absence in comments`() {
    checkCloudAndLocal("a.kt",
                       """
// could be a misattached nested conj
    """)
  }

  private fun checkCloudAndLocal(fileName: String, text: String) {
    initLocalProcessing()
    myFixture.configureByText(fileName, text)
    myFixture.checkHighlighting()

    checkCloud(fileName, text)
  }

  private fun checkCloud(fileName: String, text: String) {
    runWithCloudProcessing {
      myFixture.configureByText(fileName, text)
      myFixture.checkHighlighting()
    }
  }

  @NeedsCloud
  @Test
  fun testRuleHighlighting_txt() {
    configureByText("a.txt", "I <GRAMMAR_ERROR descr=\"Grazie.RuleEngine.En.Grammar.SUBJECT_VERB_AGREEMENT\">are</GRAMMAR_ERROR> a very humble person. " +
                                       "Mary lost control <GRAMMAR_ERROR descr=\"Grazie.RuleEngine.En.Grammar.POLARITY\">at a<caret>ll</GRAMMAR_ERROR>, so she went there.")
    myFixture.checkHighlighting()
    myFixture.launchAction(findSingleIntention("completely"))
    myFixture.checkResult("I are a very humble person. Mary lost control completely, so she went there.")
    GrazieConfig.update { s ->
      s.copy(checkingContext = s.checkingContext.copy(disabledLanguages = setOf(PlainTextLanguage.INSTANCE.id)))
    }
    assertEmpty(myFixture.doHighlighting().filter { i -> i.severity === HighlightSeverity.WARNING })
  }

  @NeedsCloud
  @Test
  fun testRuleInspection_html() {
    configureByText("a.html",
                    "<html><body>Hello. Suzy would like to understand the example <GRAMMAR_ERROR>at all</GRAMMAR_ERROR>.</body></html>")
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun `test avoid LT false positives where TextExtractor provides no text`() {
    configureByText("a.html",
                    """
                    <b>Hello. I need your <GRAMMAR_ERROR descr="NEED_HELPS">helps</GRAMMAR_ERROR>. Another sentence.</b> <!-- checking works overall -->
                    <code>public int compareTo(A a) {return s.length() - a.s.length();}</code> <!-- Another sentence! But not in <GRAMMAR_ERROR descr="Grazie.MLEC.En.MissingArticle: Missing article">code tag</GRAMMAR_ERROR> -->
                    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun `test missing article are suppressed in single sentence comments`() {
    configureByText("a.html",
      """
      <!-- But not in code tag -->
                    
      <!-- this is mistake. -->
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  @Test
  fun `test highlighting in properties`() {
    configureByText("a.properties", """
      with.error=I <GRAMMAR_ERROR>are</GRAMMAR_ERROR> a very humble file.
      ok1=Actual value of parameter ''{0}'' is always ''{1}''
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  @NeedsCloud
  fun `test honor missing article settings`() {
    checkCloud(".txt", """
      I'll do this <GRAMMAR_ERROR descr="Grazie.MLEC.En.All: Incorrect preposition">in</GRAMMAR_ERROR> Tuesday. 
      this is <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.MISSING_ARTICLE">mistake</GRAMMAR_ERROR>. 
      I am <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.MISSING_ARTICLE">engineer</GRAMMAR_ERROR> with education.
    """.trimIndent())

    GrazieConfig.update { it.copy(userDisabledRules = setOf("Grazie.RuleEngine.En.Grammar.MISSING_ARTICLE")) }
    checkCloud(".txt", """
      I'll do this <GRAMMAR_ERROR descr="Grazie.MLEC.En.All: Incorrect preposition">in</GRAMMAR_ERROR> Tuesday. 
      this is mistake. 
      I am engineer.
    """.trimIndent())
  }

  @NeedsCloud
  @Test
  fun `test no partial MLEC warnings when a rule highlights another part`() {
    configureByText("a.txt", """
      I refuse <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.GERUND_VS_INFINITIVE">meeting</GRAMMAR_ERROR> him.
      We refused <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.GERUND_VS_INFINITIVE">paying</GRAMMAR_ERROR> them.
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun `test LT style warnings are highlighted as style`() {
    configureByText("a.txt", """
      She bought it for <STYLE_SUGGESTION>$500</STYLE_SUGGESTION> dollars.
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun `test no spelling out numbers in code comments`() {
    configureByText("a.java", """
      class C {
        void foo(int x) {
          assert x == 39; // 13 dirs of 3 files
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun `test no very abuse in code comments`() {
    configureByText("a.java", """
      class C {
        void foo(int x) {
          assert x == 39; // It's a very important assertion.
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun `test markdown with local models`() {
    initLocalProcessing()

    myFixture.configureByText("a.md", """
      - `shortDescription` - [MultiformatMessageString object]. Contains the field `text` with the name of an inspection as a value. 
      
      - **/data/results**: directory to store the analysis results, needs to be empty before each Qodana run 
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  @NeedsCloud
  fun `test markdown with cloud models`() {
    checkCloud("a.md", """
      - `shortDescription` - [MultiformatMessageString object]. Contains the field `text` with the name of an inspection as a value. 
      
      - **/data/results**: directory to store the analysis results, needs to be empty before each Qodana run
       
      Have you tried <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Spelling.COMMON_TYPOS">a[ples</GRAMMAR_ERROR>? Would you like one?
    """.trimIndent().trimIndent())
  }


  @Test
  fun `test html with local models`() {
    initLocalProcessing()

    myFixture.configureByText("a.html", """<html><body>
      <p>You can configure this scenario either in the <code style="inline:term">qodana.yaml</code> file or invoke it using the CLI.</p> 
      
      </body></html>
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun `test xml with local models`() {
    initLocalProcessing()

    myFixture.configureByText("a.xml", """
      <root>
        <code style="block" lang="shell">
          docker run ... jetbrains/%linter-shell% \
          --property=idea.required.plugins.id=JavaScript,org.intellij.grails \
          --property=idea.suppressed.plugins.id=com.intellij.spring.security
        </code>
      </root>
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun `test treating markup as quotes`() {
    configureByText("a.md", """
      From the toolbar, click _Add link_, then select **is duplicated by**.
      **This** is still <GRAMMAR_ERROR>an </GRAMMAR_ERROR>mistake.
      This happened in *Tuesday*.
      Import a *Workflow*
    """.trimIndent())
    myFixture.checkHighlighting()

    Registry.get("grazie.html.concatenate.inline.tag.contents").setValue(true, testRootDisposable)
    configureByText("a.html", """
      <body>
      <p>Use the <b>Ignore assignments in and returns from private methods</b> option to ignore assignments and returns in <code>private</code> methods.
      <p>Use the <b>Ignore single field static imports</b> checkbox to ignore single-field <code>import static</code> statements.</p>
      <p>There are several reasons synchronization on <code>this</code> or <code>class</code> expressions may be a bad idea:</p>
      <p><b>This</b> is still <GRAMMAR_ERROR>an </GRAMMAR_ERROR>mistake.</p>
      </body>
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun `test highlighting a large comment`() {
    val line = "            | guest            | segment                 | booker          | guest     | segment               | autumn |; \n"
    configureByText("a.java", "/*\n" + line.repeat(100) + "*/")
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun `test no quote ordering checks in comments`() {
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)

    myFixture.configureByText("a.java",
                              """
        // There are some English words and some other cases, e.g. "h", "--help"
        
        // Did Mom really say, “Don’t eat the <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Punctuation.QUOTE_PUNCTUATION">cookies?”</GRAMMAR_ERROR>
        
        /** There are some English words and some other cases, e.g. "h", "--help" */
      """.trimIndent()
    )
    myFixture.checkHighlighting()

    myFixture.configureByText("a.md", """
      There are some English words and some other cases, e.g. "<STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Typography.VARIANT_QUOTE_PUNCTUATION">h",</STYLE_SUGGESTION> "--help"
            
      The quote check is *also* working in fragments "with <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Typography.VARIANT_QUOTE_PUNCTUATION">markup",</STYLE_SUGGESTION> right?
     """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun `test formal style`() {
    GrazieConfig.update { it.copy(styleProfile = "Formal") }

    myFixture.configureByText("a.md", """
      Mary lost control and went there <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.SMILEY_OR_EMOJI_USE">:)</STYLE_SUGGESTION>
      
      <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.SMILEY_OR_EMOJI_USE">✔ </STYLE_SUGGESTION>This is an emoji.
      
      ✓ This is not en emoji.
      """.trimIndent())
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun `test alex style overriding by Grazie rules`() {
    GrazieConfig.update { it.copy(userEnabledRules = it.userEnabledRules + "Grazie.RuleEngine.En.Style.ABLEISM") }
    myFixture.configureByText("a.md", """
      She is <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.ABLEISM">anorexic</STYLE_SUGGESTION>,
      and he is <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.ABLEISM">bulimic</STYLE_SUGGESTION>.
      The IDE can be in "dumb mode"; and the actions can be dumb-aware.
      But <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.ABLEISM">dumb</STYLE_SUGGESTION> people are a no-no.
      """.trimIndent())
    myFixture.checkHighlighting()
  }


  @NeedsCloud
  @Test
  fun `test LT Oxford spelling rules are synchronized with our setting`() {
    enableLanguages(setOf(Lang.BRITISH_ENGLISH), project, testRootDisposable)
    assertFalse(GrazieConfig.get().useOxfordSpelling)
    assertNotEmpty(GrazieConfig.get().userDisabledRules.filter { it.contains("OXFORD_SPELLING") })

    myFixture.configureByText("a.txt", "Summarising a text is great!")
    myFixture.checkHighlighting()

    GrazieConfig.update { it.withOxfordSpelling(true) }
    EdtInvocationManager.invokeAndWaitIfNeeded {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    assertEmpty(GrazieConfig.get().userDisabledRules.filter { it.contains("OXFORD_SPELLING") })
    assertNotEmpty(myFixture.doHighlighting())
  }

  @Test
  fun `test no-suggestion rules concede to MLEC`() {
    myFixture.configureByText("a.txt", "The future development would <GRAMMAR_ERROR>be <caret>tends</GRAMMAR_ERROR> to increase the horsepower.")
    myFixture.checkHighlighting()
    myFixture.findSingleIntention("tend")
  }

  @NeedsCloud
  @Test
  fun `test empty problem suppressions do not break highlighting`() {
    GrazieConfig.update { it.copy(suppressingContext = SuppressingContext(setOf("|This is an mistake."))) }

    myFixture.configureByText("a.txt", "This is <GRAMMAR_ERROR>an </GRAMMAR_ERROR>mistake.")
    myFixture.checkHighlighting()
  }

  @NeedsCloud
  @Test
  fun `test no grammar mistakes in suppression text`() {
    myFixture.configureByText(
      "a.properties",
      """
        # suppress inspection "SpellCheckingInspection" for whole file
        property=This is <GRAMMAR_ERROR>an </GRAMMAR_ERROR>mistake
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  @Test
  @NeedsCloud
  fun `test honor domain settings`() {
    // Grazie.RuleEngine.En.Style.VERY_ABUSE and Grazie.RuleEngine.En.Typography.NUMBERS_WITH_UNITS
    // rules are disabled by default in Code Comments and Code Documentation domains,
    // but enabled in Academic Text Style
    GrazieConfig.update { it.copy(styleProfile = "Academic") }
    checkCloud("C.java", """
      /**
       * It’s very fragile.
       *
       * Also in this experiment, 2mg of the extract was dissolved in water.
       */
      class C {
          // It’s very fragile.
          void foo(int x) {}
          
          // Also in this experiment, 2mg of the extract was dissolved in water.
      }
    """.trimIndent())

    checkCloud(".md", """
      It’s <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VERY_ABUSE">very fragile</STYLE_SUGGESTION>.
      
      Also in this experiment, <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Typography.NUMBERS_WITH_UNITS">2mg</STYLE_SUGGESTION> of the extract was dissolved in water.
    """.trimIndent())
  }

  @NeedsCloud
  @Test
  fun `test aggregator provides relevant suggestions`() {
    configureByText("a.java", "// <caret>Jim get over here!")
    myFixture.doHighlighting()

    // Suggestions are reordered by [TextProblemAggregator] starting with the most meaningful ones
    val intentions = availableIntentions
    assertEquals(intentions[1].text, "Jim, get")
    assertEquals(intentions[2].text, "Jim gets")
  }

  @NeedsCloud
  @Test
  fun `test articles before url are ignored`() {
    checkCloudAndLocal(
      "a.java",
      """
        // If we know that @currentToken is a url, we will strictly use it (--url).
        // If we know that @currentToken is an url, we will strictly use it (--url).
      """.trimIndent()
    )
  }

  companion object {
    @JvmStatic
    fun enableLanguages(langs: Set<Lang>, project: Project, disposable: Disposable) {
      EdtInvocationManager.invokeAndWaitIfNeeded {
        GrazieConfig.update { it.copy(enabledLanguages = langs) }
        UIUtil.dispatchAllInvocationEvents()
      }
      GrazieTestBase.loadLangs(langs, project)
      Disposer.register(disposable) { GrazieTestBase.unloadLangs(project) }
    }

    @JvmStatic
    fun enableRules(vararg ids: String) {
      GrazieConfig.update { it.copy(userEnabledRules = it.userEnabledRules + setOf(*ids)) }
    }
  }

}