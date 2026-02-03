def foo = [1, 2, 5]
def bar = [00, 11, 22, 33, 44, 55, 66, 77, 88]

// highlights right side as 'Can not assign Integer to Collection'
Collection<Integer> baz = bar[foo]
assert baz == [11, 22, 55]

// highlights right side as 'Can not assign Integer to Collection'
Collection<Integer> qux = bar[[1, 2, 5]]
assert qux == [11, 22, 55]

// accepted as correct
Collection<Integer> quux = [bar[1], bar[5], bar[2]]
assert quux == [11, 22, 55]

// highlights right side as 'Can not assign Integer to Collection'

Collection<Integer> quuux = bar[2..5]
assert quuux == [22, 33, 44, 55]
