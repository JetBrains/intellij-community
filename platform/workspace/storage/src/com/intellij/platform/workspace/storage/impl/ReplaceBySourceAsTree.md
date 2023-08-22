# Replace By Source as a tree

- Make type graphs. Separate graphs into independent parts (how is it called correctly?)
- Work on separate graph parts as on independent items
- Get entities by EntitySource. Take the first entity. Get the graph part.

Taking the entities:

       Root
      /    \
    One    [Two]

Replace With

        Root2
       /    \
     One2    [Two2]

- Take `[Two]`, get `Root` using links.
- Find `Root2`
  - By `SymbolicId` (using symbolicId index)
  - Or ???
- Check if `Root` should be replaced with `Root2` (no)
- Traverse children.

Process the root, then go to the children, but take only the path where
the replacement should be performed.
Keep track on parents that were already replaced

- Search children by `anotherEquals`.
If there is more than one, use `equals`


Taking the fact that we have an entity and associated entity in the another store.

# Algo



