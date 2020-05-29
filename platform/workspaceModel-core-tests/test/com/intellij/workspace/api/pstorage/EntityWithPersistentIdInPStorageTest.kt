package com.intellij.workspace.api.pstorage

import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.workspace.api.pstorage.entities.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntityWithPersistentIdInPStorageTest {
  @Test
  fun `add remove entity`() {
    val builder = PEntityStorageBuilder.create()
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.removeEntity(bar)
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change target entity name`() {
    val builder = PEntityStorageBuilder.create()
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.modifyEntity(ModifiableLinkedListEntity::class.java, bar) {
      name = "baz"
    }
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change name in reference`() {
    val builder = PEntityStorageBuilder.create()
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    val baz = builder.addLinkedListEntity("baz", LinkedListEntityId("foo"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    val newFoo = builder.modifyEntity(ModifiableLinkedListEntity::class.java, foo) {
      next = LinkedListEntityId("baz")
    }
    builder.assertConsistency()
    assertEquals(baz, newFoo.next.resolve(builder))
  }

  @Test
  fun `remove child entity with parent entity`() {
    val builder = PEntityStorageBuilder.create()
    val parent = builder.addPParentEntity("parent")
    builder.addPChildEntity(parent)
    builder.assertConsistency()
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEmpty(builder.entities(PChildEntity::class.java).toList())
  }
}