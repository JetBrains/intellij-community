package com.intellij.cce.evaluable.chat

import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.java.chat.JavaApiCallExtractor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

class JavaApiCallExtractorTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  private fun createTokenProperties(methodName: String): TokenProperties {
    return object : TokenProperties {
      override val tokenType = TypeProperty.UNKNOWN
      override val location = SymbolLocation.UNKNOWN

      override fun additionalProperty(name: String): String? {
        return if (name == METHOD_NAME_PROPERTY) methodName else null
      }

      override fun additionalPropertyNames(): Set<String> {
        return setOf(METHOD_NAME_PROPERTY)
      }

      override fun describe(): String {
        return "Mock Token Properties"
      }

      override fun hasFeature(feature: String): Boolean {
        return false
      }

      override fun withFeatures(features: Set<String>): TokenProperties {
        return this
      }
    }
  }

  fun `test extractApiCalls with single method call`() {
    val code = """
            public class MyClass {
                public void foo() {
                    bar();
                }
                private void bar() {
                    // Does nothing
                }
            }
        """.trimIndent()

    val tokenProperties = createTokenProperties("foo")
    val project: Project = project

    runBlocking {
      val extractor = JavaApiCallExtractor()
      val apiCalls = extractor.extractApiCalls(code, project, tokenProperties)
      assertEquals(listOf("MyClass#bar"), apiCalls)
    }
  }

  fun `test extractApiCalls with multiple method calls`() {
    val code = """
            public class MyClass {
                public void foo() {
                    bar();
                    baz();
                }
                private void bar() {
                    // Does nothing
                }
                private void baz() {
                    // Does nothing
                }
            }
        """.trimIndent()

    val tokenProperties = createTokenProperties("foo")
    val project: Project = project

    runBlocking {
      val extractor = JavaApiCallExtractor()
      val apiCalls = extractor.extractApiCalls(code, project, tokenProperties)
      assertEquals(listOf("MyClass#bar", "MyClass#baz"), apiCalls)
    }
  }

  fun `test extractApiCalls with no method calls`() {
    val code = """
            public class MyClass {
                public void foo() {
                    // Does nothing
                }
            }
        """.trimIndent()

    val tokenProperties = createTokenProperties("foo")
    val project: Project = project

    runBlocking {
      val extractor = JavaApiCallExtractor()
      val apiCalls = extractor.extractApiCalls(code, project, tokenProperties)
      assertTrue(apiCalls.isEmpty())
    }
  }

  fun `test extractApiCalls with non-existent method`() {
    val code = """
            public class MyClass {
                public void foo() {
                    something()
                }
            }
        """.trimIndent()

    val tokenProperties = createTokenProperties("bar")
    val project: Project = project

    runBlocking {
      val extractor = JavaApiCallExtractor()
      val apiCalls = extractor.extractApiCalls(code, project, tokenProperties)
      assertTrue(apiCalls.isEmpty())
    }
  }

  fun `test extractApiCalls on real sample`() {
    val code = """
            // Import relevant packages
import com.badlogic.gdx.graphics.g2d.Batch;

public class MySubClass extends SuperClass {
    private boolean alive;
    private Velocity velocity;

    @Override
    public void draw(Batch batch) {
        // Check if the status is alive
        if (!alive) {
            return;
        }

        // Perform the Targeting check and velocity length check
        if (!isTargetingSuccessful() || velocity.getLength() <= 5) {
            // If either check fails, set alive to false
            alive = false;

            // Optionally trigger additional action
            onTargetingFailOrLowVelocity();

            return;
        }

        // Call the superclass draw method if all checks pass
        super.draw(batch);
    }

    // Method to simulate a targeting success check (replace with actual logic)
    private boolean isTargetingSuccessful() {
        // Placeholder for actual targeting logic
        return true; // Or false depending on the actual check logic
    }

    // Method to handle actions when targeting fails or velocity is low
    private void onTargetingFailOrLowVelocity() {
        // Placeholder for actual action
        System.out.println("Targeting failed or low velocity!");
    }
}

// Assuming Velocity is another class defined elsewhere
class Velocity {
    private float length;

    // Constructor and other methods

    public float getLength() {
        return length;
    }
}

// Assuming SuperClass is another class defined elsewhere
class SuperClass {
    public void draw(Batch batch) {
        // Superclass draw logic
    }
}
        """.trimIndent()

    val tokenProperties = createTokenProperties("draw")
    val project: Project = project

    runBlocking {
      val extractor = JavaApiCallExtractor()
      val apiCalls = extractor.extractApiCalls(code, project, tokenProperties)
      assertEquals(
        listOf("MySubClass#isTargetingSuccessful", "Velocity#getLength", "MySubClass#onTargetingFailOrLowVelocity"),
        apiCalls
      )
    }
  }
}
