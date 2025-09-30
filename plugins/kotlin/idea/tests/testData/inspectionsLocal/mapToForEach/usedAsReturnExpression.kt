// PROBLEM: none

fun foo(nums: List<Int>) = nums.m<caret>ap(::println)

fun main() {
    val x: List<Unit> = foo(listOf(1, 2, 3))
}

// IGNORE_K1
