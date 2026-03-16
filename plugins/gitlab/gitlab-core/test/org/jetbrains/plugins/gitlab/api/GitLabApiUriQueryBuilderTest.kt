// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class GitLabApiUriQueryBuilderTest {

  @Test
  fun `test simple string parameter`() {
    val result = GitLabApiUriQueryBuilder.build {
      "key" eq "value"
    }
    assertEquals("key=value", result)
  }

  @Test
  fun `test simple integer parameter`() {
    val result = GitLabApiUriQueryBuilder.build {
      "count" eq 42
    }
    assertEquals("count=42", result)
  }

  @Test
  fun `test simple boolean parameter`() {
    val result = GitLabApiUriQueryBuilder.build {
      "enabled" eq true
      "disabled" eq false
    }
    assertEquals("enabled=true&disabled=false", result)
  }

  @Test
  fun `test null values are skipped`() {
    val result = GitLabApiUriQueryBuilder.build {
      "present" eq "value"
      "nullString" eq null as String?
      "nullInt" eq null as Int?
      "nullBoolean" eq null as Boolean?
      "nullList" eq null as List<Any>?
    }
    assertEquals("present=value", result)
  }

  @Test
  fun `test list parameter`() {
    val result = GitLabApiUriQueryBuilder.build {
      "ids" eq listOf(1, 2, 3)
    }
    assertEquals("ids[]=1&ids[]=2&ids[]=3", result)
  }

  @Test
  fun `test empty list is put as empty`() {
    val result = GitLabApiUriQueryBuilder.build {
      "before" eq "value"
      "empty" eq emptyList<Int>()
      "after" eq "another"
    }
    assertEquals("before=value&empty[]=&after=another", result)
  }

  @Test
  fun `test nested object`() {
    val result = GitLabApiUriQueryBuilder.build {
      "position" {
        "base_sha" eq "abc123"
        "head_sha" eq "def456"
      }
    }
    assertEquals("position[base_sha]=abc123&position[head_sha]=def456", result)
  }

  @Test
  fun `test deeply nested objects`() {
    val result = GitLabApiUriQueryBuilder.build {
      "position" {
        "line_range" {
          "start" {
            "line_code" eq "123"
          }
        }
      }
    }
    assertEquals("position[line_range][start][line_code]=123", result)
  }

  @Test
  fun `test mixed flat and nested parameters`() {
    val result = GitLabApiUriQueryBuilder.build {
      "note" eq "comment text"
      "reviewer_ids" eq listOf(1, 2, 3)
      "position" {
        "base_sha" eq "sha1"
        "head_sha" eq "sha2"
      }
    }
    assertEquals("note=comment+text&reviewer_ids[]=1&reviewer_ids[]=2&reviewer_ids[]=3&position[base_sha]=sha1&position[head_sha]=sha2",
                 result)
  }

  @Test
  fun `test URL encoding of special characters`() {
    val result = GitLabApiUriQueryBuilder.build {
      "message" eq "Hello World!"
      "path" eq "src/main/java"
      "special" eq "a=b&c=d"
    }
    assertEquals("message=Hello+World%21&path=src%2Fmain%2Fjava&special=a%3Db%26c%3Dd", result)
  }

  @Test
  fun `test complex nested structure with multiple types`() {
    val result = GitLabApiUriQueryBuilder.build {
      "note" eq "Review comment"
      "resolvable" eq true
      "position" {
        "base_sha" eq "abc"
        "start_sha" eq "def"
        "head_sha" eq "ghi"
        "position_type" eq "text"
        "new_line" eq 10
        "line_range" {
          "start" {
            "line_code" eq "code1"
            "type" eq "new"
          }
          "end" {
            "line_code" eq "code2"
            "type" eq "new"
          }
        }
      }
    }
    val expected =
      "note=Review+comment&resolvable=true&position[base_sha]=abc&position[start_sha]=def&position[head_sha]=ghi" +
      "&position[position_type]=text&position[new_line]=10" +
      "&position[line_range][start][line_code]=code1&position[line_range][start][type]=new" +
      "&position[line_range][end][line_code]=code2&position[line_range][end][type]=new"
    assertEquals(expected, result)
  }

  @Test
  fun `test empty builder produces empty string`() {
    val result = GitLabApiUriQueryBuilder.build { }
    assertEquals("", result)
  }

  @Test
  fun `test multiple nested objects at same level`() {
    val result = GitLabApiUriQueryBuilder.build {
      "object1" {
        "field1" eq "value1"
      }
      "object2" {
        "field2" eq "value2"
      }
    }
    assertEquals("object1[field1]=value1&object2[field2]=value2", result)
  }
}