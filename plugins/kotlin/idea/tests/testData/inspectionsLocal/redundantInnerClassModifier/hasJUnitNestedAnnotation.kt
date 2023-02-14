// PROBLEM: none
package org.junit.jupiter.api

annotation class Nested

class SampleTest {
    @Nested
    <caret>inner class TestSmth {}
}