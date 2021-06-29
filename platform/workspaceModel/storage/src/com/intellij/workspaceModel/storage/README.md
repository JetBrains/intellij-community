# Workspace model specification

# Persistent Id

TODO

# Hard references

Hard reference is a usual way to connect entities. This mechanism provides several types of entities connection,
including `One-To-One` and `One-To-Many`. Entities in the connection are defined as parent and child.
The parent entity may be optional for the child (nullable).
When the parent entity is removed, the child references are also cascade removed. The workspace model **guarantees**
that hard reference is always resolved (including resolution to null for nullable references).
It's not needed to define the connection on both entities, defining the connection only on one side is enough.

#### TODO questions and improvements

**Optional parent**  
Do we need entities with an optional parent?
If we decide that all children should have a connection to an entity with a PersistentId,
this option should not be presented.

Example of correct connection with an optional parent: Facet to Facet (underlying facets)

**Finish all possible types of the references**  
At the moment supported and specified only references that are currently used by intellij project model.
We should implement and specify all remaining types of references.


#### One-To-Many

- One-To-Many
  - One-To-Many with optional parent
  - One-To-Many with mandatory parent

#### One-To-Abstract-Many

This reference has a special behaviour. During addDIff it doesn't try to merge added and remove children,
but just replaces all children.
This behaviour should be updated if you want a version of this reference that merges children

- One-To-Abstract-Many  
  - One-To-Abstract-Many with optional parent

Almost the same as `One-To-Many`, but allowing to reference to children of abstract classes

#### One-To-One
In `one-to-one` connections **child is always optional for the parent**.  
This is the restriction of the current specification. See the explanation below.

- One-To-One  
  - One-To-One with mandatory parent
  - One-To-One with optional parent

⚠️ If you set a new child for the `One-To-One` reference with a mandatory parent, the old child is automatically
removed to keep the store consistent.
For the `One-To-One` with optional parent, the previous child remains in the store.

#### One-To-Abstract-One
In `one-to-abstract-one` connections **child is always optional for the parent**.  
This is the restriction of the current specification. See the explanation below.

- One-To-Abstract-One  
  - One-To-Abstract-One with optional parent

#### One-To-One missing mandatory child

It's not allowed to have a mandatory child reference in `One-To-One` connections.
This is made because of the following reasons:
- Since the cascade delete doesn't work from child to parent,
  a mandatory child reference will constrain child entity deletion.
  The deletion of mandatory child cannot be verified by the compiler, so this type of reference will
  introduce additional exceptions in the project model.
- The inability to remove child reference may introduce complicated conditional behaviour changes.  
  E.g. let's say we have a `ModuleEntity` and one-to-many connected `ContentRootEntity`. So, after the removing
  the `ModuleEntity` we expect that all `ContentRootEntity`-ies would be removed as well.
  However, if some external plugin adds a parent for the `ContentRootEntity` with one-to-one connection,
  the removing of `ContentRootEntity`-ies is no more possible. So, the behaviour of the workspace model and
  the IDE changes depending on persistence of this external plugin.

  _This issue makes the behaviour of workspace model more unpredictable for the users._


# Soft references

Soft reference (or reference by `PersistentId`) a second option to define a connection between entities.
Only entities with a `PersistentId` may be referred with a soft reference. To define a soft reference,
put a `PersistentId` (e.g. `ModuleId` or `LibraryId`) as a property of your entity.
Workspace model **guarantees** that in case of the `PersistentId` of the referred entity changes, the entity
with an id updates as well to keep the reference actual. So, if you have a reference to a module with name `A`: `ModuleId(A)`,
the reference will be automatically updated if the module is renamed from `A` to `B`.

Workspace Model **does not guarantee** that the referred entities are really exist in the store.
So, the accessing the entity by soft reference may return null.

Workspace Model **does not** perform cascade removing for soft references, as it does for hard references.

# Entities removing

Entities removing is preformed with cascade removal of entities that are connected as children.
E.g. on removing `ModuleEntity`, all connected `ContentRootEntity` will also be removed.

# Builder changes

Builder of the workspace model may generate all events with changes that were performed on this builder.
This mechanism may be used to send events.

The changes are generated only for modified entity.
If the change was performed on two entities at the same time (this may happen on updating hard reference),
only one event of explicitly updated entity is generated.

TODO: Should we generate two events?

  
# Function `replaceBySource`

`replaceBySource` is a function that allows to replace a part of the store to a different store
depending on an entity source.
This is a powerful mechanism that allows, for example, replace all existing gradle imported entities with a newly 
created after the gradle refresh. `replaceBySource` function tries to keep the references between entities even if they
were replaced.

**XXX the described implementation is currently different from the actual one**

# Function `addDiff`

`addDiff` is one of approaches of joining a lot of changes into a different store. When creating a new builder,
all changes that are applied to it are recorded and may also be applied to a different builder.

# Indexes

TODO

# Path fields

TODO
