# Workspace model specification

# Definitions

- Storage, or workspace storage - the core storage where all entities are saved.  
  _Please **do not** use the term `store`, it's deprecated for now_.
- Builder - a builder that can be used to create a new workspace storage or modify the existing one.  
  _Please **do not** use the term `diff`, it's deprecated for now_.
- Hard reference - a reference between two entities that is always resolved.
- Soft reference - a symbolic link to a different entity with `PersistentId`. 
  The referred entity may be not presented in the storage.
- Persistent id - a user presentable id of some entity.
- Index - index inside the storage that helps to quickly access entities by its properties.
- External index - a map of user defined objects to entities. This index is not stored to storage cache on disk.

# General information

TODO

- Immutable structure
- Thread save on read w/o locks

# Persistent Id

Persistent Id is an id in some entity types. This id is supposed to be end-user readable.
With this persistent id you can identify and quickly access an entity.  
An important usage of persistent id is soft references. Some entity may contain a persistent id of a different entity
what is defined as a soft reference. If the referenced entity changes its persistent id, the id is automatically updated
in all entity that hold a reference to this id.

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

⚠️ If you set a new children for the `One-To-Many` reference with a mandatory parent, the old children are automatically
removed if they are not presented in a new set.
For the `One-To-Many` with optional parent, the previous children remain in the storage.

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
removed to keep the storage consistent.
For the `One-To-One` with optional parent, the previous child remains in the storage.

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

Soft reference (or reference by `PersistentId`, or symbolic link) a second option to define a connection between entities.
Only entities with a `PersistentId` may be referred with a soft reference. To define a soft reference,
put a `PersistentId` (e.g. `ModuleId` or `LibraryId`) as a property of your entity.
Workspace model **guarantees** that in case of the `PersistentId` of the referred entity changes, the entity
with an id updates as well to keep the reference actual. So, if you have a reference to a module with name `A`: `ModuleId(A)`,
the reference will be automatically updated if the module is renamed from `A` to `B`.

Workspace Model **does not guarantee** that the referred entities are really exist in the storage.
So, the accessing the entity by soft reference may return null.

Workspace Model **does not** perform cascade removing for soft references, as it does for hard references.

# Entities removing

Entities removing is preformed with cascade removal of entities that are connected as children.
E.g. on removing `ModuleEntity`, all connected `ContentRootEntity` will also be removed.

# Modifying entities

Remove entities on modifying references

# Builder changes

Builder of the workspace model may generate all events with changes that were performed on this builder.
This mechanism may be used to send events.

The changes are generated only for modified entity.
If the change was performed on two entities at the same time (this may happen on updating hard reference),
only one event of explicitly updated entity is generated.

TODO: Should we generate two events?

  
# Function `replaceBySource`

`replaceBySource` is a function that allows to replace a part of the storage to a different storage
depending on an entity source.
This is a powerful mechanism that allows, for example, replace all existing gradle imported entities with a newly 
created after the gradle refresh. `replaceBySource` function tries to keep the references between entities even if they
were replaced.

**XXX the described implementation is currently different from the actual one**

The process of `replaceBySource`:

By "target builder" or "target" we mean the builder ther would be updated after the operation.
This is the receiver of the function

By "source storage" or "source" we mean the storage that is merging into the target builder.

The general approach for performing `replaceBySource` can be described as following:

- Traverse entities from the parents to children
- Trying to match entities based on `PersistentId`
- Removing entities from target builder that match the filter
- Add entities from source storage that match the filter
- Trying not to touch entities that don't match the filter. They can be removed cascade, though.

<!--

WIP

Possible combinations:
- With or without `PersistentId`
- With or without parent entity
- With matching or not-matching entity source

1. Collect all entities that fit the entity source in target and source.
2. Take a random entity from the ones that fit.
3. Trying to build the graph of entity types.
4. Detecting the root entity type for the chosen type.  
   **TODO: what about cyclic dependencies? Or dependencies to the same type as in FacetEntity?**
5. 
-->

# Function `addDiff`

`addDiff` is one of approaches of joining a lot of changes into a different storage. When creating a new builder,
all changes that are applied to it are recorded and may also be applied to a different builder.

# Indexes

TODO

# Path fields

TODO
